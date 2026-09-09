package com.umesh.hotelbooking.webhook;

import com.umesh.hotelbooking.crypto.SignatureVerificationException;
import com.umesh.hotelbooking.crypto.SignatureVerifier;
import com.umesh.hotelbooking.security.PayloadRedactor;
import com.umesh.hotelbooking.service.PaymentSettlementService;
import com.umesh.hotelbooking.repository.WebhookEventLogStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

/**
 * Transcribes design doc 12.3's verification sequence step for step — the step numbers below
 * are the design document's own, so a reviewer can check the transcription at a glance.
 *
 * <p>Steps 2 (dedupe) and 3–4 (HMAC) are deliberately in that order: the cheap dedupe check
 * runs before the more expensive signature computation, which is safe because a duplicate
 * {@code eventId} was already signature-verified on its first arrival.
 *
 * <p>Step 7 (process) never reimplements the settlement logic the status-check ladder and
 * admin manual-review resolution already have — it goes through {@link
 * PaymentSettlementService}, exactly like they do (design doc task spec §8.4). That includes
 * the late-settlement-after-an-expired-hold case (design doc 9.2 scenario 2): {@code
 * PaymentSettlementService.settle} already decides confirm-vs-reverse based on whether the
 * inventory hold is still live, so this class does not need its own copy of that branch.
 */
@Service
public class InboundWebhookService {

    private static final Logger log = LoggerFactory.getLogger(InboundWebhookService.class);

    private final WebhookEventLogStore logStore;
    private final SignatureVerifier signatureVerifier;
    private final PayloadRedactor payloadRedactor;
    private final PaymentSettlementService settlementService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InboundWebhookService(WebhookEventLogStore logStore,
                                 SignatureVerifier signatureVerifier,
                                 PayloadRedactor payloadRedactor,
                                 PaymentSettlementService settlementService,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.logStore = logStore;
        this.signatureVerifier = signatureVerifier;
        this.payloadRedactor = payloadRedactor;
        this.settlementService = settlementService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * @throws SignatureVerificationException steps 1, 3–4: timestamp outside the replay
     *     window, or a signature that does not verify. Caught by {@code
     *     WebhookExceptionHandler}, the one SYSTEM-category case where a non-2xx is correct.
     */
    public WebhookAck receive(String providerCode, String signature, long timestampEpochMillis,
                              byte[] rawBody, String correlationId) {
        // 1. Timestamp within the replay window.
        signatureVerifier.verifyTimestamp(timestampEpochMillis);

        ParsedEnvelope parsed = parse(rawBody);
        if (parsed == null) {
            // Malformed JSON has no eventId to key a log row on or dedupe against — this is a
            // processing anomaly, not a signature problem, so it still acks 200 (step 8).
            log.error("Webhook body from {} could not be parsed [correlationId={}]", providerCode, correlationId);
            return new WebhookAck(null, "RECEIVED");
        }

        // 2. eventId not already seen — cheap, so it runs before the more expensive HMAC step.
        if (logStore.existsByProviderCodeAndEventId(providerCode, parsed.eventId())) {
            return new WebhookAck(parsed.eventId(), "DUPLICATE");
        }

        // 3-4. HMAC-SHA256 over the raw bytes, constant-time compare.
        try {
            signatureVerifier.verifySignature(providerCode, rawBody, signature);
        } catch (SignatureVerificationException e) {
            // A rejected callback is exactly the one worth a record of.
            persist(providerCode, parsed, false, WebhookOutcome.SIGNATURE_INVALID,
                    redact(rawBody), correlationId, Instant.now(clock));
            // Re-thrown with the now-known eventId so the rejection ack can echo it.
            throw new SignatureVerificationException(e.errorCode(), e.getMessage(), parsed.eventId());
        }

        // 5. Redact — only now, after verification, never before (it would break the HMAC).
        String redactedPayload = redact(rawBody);

        // 6. Persist before process: a crash mid-processing must still be reconstructible.
        // Starts pessimistic (PROCESSING_FAILED) and is flipped to PROCESSED below only once
        // processing genuinely succeeds — accurate if a crash happens in between.
        WebhookEventLog logRow = persist(providerCode, parsed, true, WebhookOutcome.PROCESSING_FAILED,
                redactedPayload, correlationId, Instant.now(clock));
        if (logRow == null) {
            // Lost the race on the unique constraint: a concurrent delivery of the same
            // eventId claimed it between our dedupe check and this insert.
            return new WebhookAck(parsed.eventId(), "DUPLICATE");
        }

        // 7. Process idempotently through the FSM.
        WebhookOutcome outcome;
        try {
            outcome = process(parsed, correlationId);
        } catch (RuntimeException e) {
            log.error("Webhook processing failed: provider={} eventId={} [correlationId={}]",
                    providerCode, parsed.eventId(), correlationId, e);
            outcome = WebhookOutcome.PROCESSING_FAILED;
        }
        updateOutcome(logRow.getId(), outcome, Instant.now(clock));

        // 8. Return 200 regardless of the business outcome.
        return new WebhookAck(parsed.eventId(), "RECEIVED");
    }

    private WebhookOutcome process(ParsedEnvelope parsed, String correlationId) {
        WebhookEventType type;
        try {
            type = WebhookEventType.valueOf(parsed.eventType());
        } catch (IllegalArgumentException e) {
            // An unknown event type is a no-op, not a deserialisation failure — a provider
            // adding a new type must not be met with a 400 and a retry storm.
            return WebhookOutcome.UNKNOWN_EVENT_TYPE;
        }

        switch (type) {
            case PAYMENT_SUCCESS -> settlementService.settleByProviderReference(parsed.providerReference(), correlationId);
            case PAYMENT_FAILED -> settlementService.failByProviderReference(parsed.providerReference());
            case REFUND_COMPLETED ->
                // Deliberate scope boundary: refunds are already driven synchronously by
                // CancellationService (Phase 5). Processing this asynchronously too would open
                // a second write path into the same ledger for the same event. Logged and
                // acked, not processed.
                    log.info("Webhook REFUND_COMPLETED received and logged only (not processed): "
                            + "providerReference={} [correlationId={}]", parsed.providerReference(), correlationId);
        }
        return WebhookOutcome.PROCESSED;
    }

    private String redact(byte[] rawBody) {
        return payloadRedactor.redact(new String(rawBody, StandardCharsets.UTF_8));
    }

    private WebhookEventLog persist(String providerCode, ParsedEnvelope parsed, boolean signatureValid,
                                    WebhookOutcome outcome, String redactedPayload, String correlationId, Instant now) {
        WebhookEventLog entity = WebhookEventLog.builder()
                .providerCode(providerCode)
                .eventId(parsed.eventId())
                .eventType(parsed.eventType())
                .signatureValid(signatureValid)
                .outcome(outcome)
                .payload(redactedPayload)
                .correlationId(correlationId)
                .receivedAt(now)
                .build();
        try {
            return logStore.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    private void updateOutcome(Long id, WebhookOutcome outcome, Instant processedAt) {
        logStore.findById(id).ifPresent(row -> {
            row.setOutcome(outcome);
            row.setProcessedAt(processedAt);
            logStore.save(row);
        });
    }

    private record ParsedEnvelope(String eventId, String eventType, String providerReference) {
    }

    private ParsedEnvelope parse(byte[] rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String eventId = textOrNull(root, "eventId");
            String eventType = textOrNull(root, "eventType");
            if (eventId == null || eventType == null) {
                return null;
            }
            String providerReference = textOrNull(root.path("payload"), "providerReference");
            return new ParsedEnvelope(eventId, eventType, providerReference);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asString();
    }
}
