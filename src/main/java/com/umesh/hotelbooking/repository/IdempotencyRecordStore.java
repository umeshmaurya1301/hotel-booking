package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.IdempotencyRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link IdempotencyRecord}, the dedupe table behind payment and booking
 * retries. Keyed by the client's message id, so the identifier type is {@code String}. */
public interface IdempotencyRecordStore {

    IdempotencyRecord save(IdempotencyRecord record);

    /**
     * The store's answer to "did I claim this {@code msgId} first?".
     *
     * <p>{@link BookingStore#saveAndFlush}'s contract, and the one place in the system where the
     * "surface the violation now" half of it is load-bearing rather than convenient: two
     * concurrent requests carrying the same message id both reach this call, and the loser must
     * find out here — by the store rejecting the duplicate key — rather than at commit, long
     * after the caller has decided it won the race and gone on to take payment. An
     * implementation that defers uniqueness checking to commit does not satisfy this port.
     */
    IdempotencyRecord saveAndFlush(IdempotencyRecord record);

    Optional<IdempotencyRecord> findById(String msgId);

    List<IdempotencyRecord> findAllById(Iterable<String> msgIds);

    List<IdempotencyRecord> findAll();

    /**
     * Removes every record older than {@code cutoff} and returns how many were removed, so the
     * sweeper has something to log.
     *
     * <p>Declared as a bulk operation, not a load-then-delete loop: a dedupe table growing
     * without bound (design doc 8a) is exactly the case where fetching every stale row into
     * memory first, just to delete it, would become the problem this method exists to avoid.
     * An implementation must delete in the store, not in the application.
     */
    int deleteByCreatedAtBefore(Instant cutoff);
}
