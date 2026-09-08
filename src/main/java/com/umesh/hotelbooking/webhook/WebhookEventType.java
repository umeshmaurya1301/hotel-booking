package com.umesh.hotelbooking.webhook;

/**
 * The business events a payment provider's callback can carry (design doc 12.1).
 *
 * <p>{@link WebhookEnvelope#eventType()} stays a raw {@code String} on the wire and is parsed
 * to this enum inside {@link InboundWebhookService}, deliberately with a safe fallback to
 * "unknown" rather than a deserialisation failure — a provider that adds a new event type
 * must be met with a logged, persisted, 200-acked no-op, not a 400 and a retry storm.
 */
public enum WebhookEventType {
    PAYMENT_SUCCESS, PAYMENT_FAILED, REFUND_COMPLETED
}
