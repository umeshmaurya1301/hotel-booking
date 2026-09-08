package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.IdempotencyRecord;
import com.umesh.hotelbooking.entity.IdempotencyStatus;
import com.umesh.hotelbooking.exception.IdempotencyConflictException;
import com.umesh.hotelbooking.exception.IdempotencyPayloadMismatchException;
import com.umesh.hotelbooking.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Idempotency layer (a) of design doc 8: client → API, keyed on the client-supplied
 * {@code msgId} rather than a separate {@code Idempotency-Key} header — two identifiers for
 * one concept is a design smell.
 *
 * <p>{@link #begin} runs in its own transaction ({@code REQUIRES_NEW}). Two genuinely
 * concurrent requests carrying the same msgId can both pass the existence check and both
 * attempt the insert; the table's primary key is what actually serialises them, surfacing as
 * a {@link DataIntegrityViolationException} on the loser. Catching that inside the caller's
 * own transaction would leave the persistence context poisoned, so the insert attempt is
 * isolated here and its failure converts cleanly into "a request is already in flight".
 *
 * <p>Simplification stated plainly: {@link #complete} runs in the <em>same</em> transaction
 * as the caller's business logic, not its own. That means an idempotency record only reaches
 * {@code COMPLETED} if the whole request commits — a crash between this table's insert and
 * the rest of the work rolls both back together, which is safe, but a record stuck at
 * {@code IN_PROGRESS} forever is a real (if narrow) production scenario this does not
 * reproduce. Building that would need a second commit boundary partway through a single
 * request, which is more machinery than this exercise's idempotency story needs to prove.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @return the stored response if {@code msgId} was already completed with an identical
     *     body; empty if this is a new request (an IN_PROGRESS row has been inserted — call
     *     {@link #complete} once processing finishes)
     * @throws IdempotencyConflictException if a request with this msgId is still in flight
     * @throws IdempotencyPayloadMismatchException if the body differs from the original
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> begin(String msgId, Object requestPayload, Class<T> responseType) {
        String hash = hash(requestPayload);

        Optional<IdempotencyRecord> existing = repository.findById(msgId);
        if (existing.isPresent()) {
            return handleReplay(existing.get(), hash, responseType);
        }

        IdempotencyRecord record = IdempotencyRecord.builder()
                .msgId(msgId)
                .requestHash(hash)
                .status(IdempotencyStatus.IN_PROGRESS)
                .createdAt(Instant.now(clock))
                .build();
        try {
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            // Lost the race on the msgId primary key: another request claimed it first.
            throw new IdempotencyConflictException(msgId);
        }
        return Optional.empty();
    }

    private <T> Optional<T> handleReplay(IdempotencyRecord record, String hash, Class<T> responseType) {
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyPayloadMismatchException(record.getMsgId());
        }
        if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new IdempotencyConflictException(record.getMsgId());
        }
        return Optional.of(deserialize(record.getResponseBody(), responseType));
    }

    /** Marks {@code msgId} COMPLETED with the response a replay should return. */
    public void complete(String msgId, Object response) {
        IdempotencyRecord record = repository.findById(msgId)
                .orElseThrow(() -> new IllegalStateException("No idempotency record for msgId " + msgId));
        record.setStatus(IdempotencyStatus.COMPLETED);
        record.setResponseBody(serialize(response));
        repository.save(record);
    }

    /** Jackson 3's write/read methods throw an unchecked {@code JacksonException} directly -
     * no checked exception to wrap here, unlike Jackson 2. */
    private String hash(Object payload) {
        byte[] json = objectMapper.writeValueAsBytes(payload);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to hash idempotency payload", e);
        }
    }

    private String serialize(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T deserialize(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
