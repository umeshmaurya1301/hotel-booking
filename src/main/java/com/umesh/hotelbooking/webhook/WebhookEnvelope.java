package com.umesh.hotelbooking.webhook;

import java.time.Instant;

/**
 * The one envelope shape used both inbound and outbound (design doc 12.1) — one envelope, one
 * signer, both directions.
 *
 * @param eventId the provider's own id — the dedup key for idempotency layer (c) (design doc
 *     8c), unrelated to and never conflated with the client-side {@code msgId} of layer (a)
 * @param eventType kept as a raw string, not {@link WebhookEventType}, so an event type this
 *     server does not recognise still deserialises — see that enum's Javadoc
 * @param providerCode which gateway sent this
 * @param eventTime the provider's own clock
 * @param version the payload contract version, echoed for observability only, exactly like
 *     {@code ApiRequest.version} — never rejected on skew
 * @param payload the operation-specific body
 */
public record WebhookEnvelope<T>(
        String eventId,
        String eventType,
        String providerCode,
        Instant eventTime,
        String version,
        T payload) {
}
