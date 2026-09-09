package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.webhook.WebhookEventLog;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link WebhookEventLog}, the inbound-webhook dedupe and audit trail.
 *
 * <p>The port lives here with the other stores rather than beside its entity in {@code webhook}:
 * a persistence seam that most aggregates go through and one bypasses is not a seam. Append-only,
 * like every other audit trail — see {@link LedgerEntryStore}.
 */
public interface WebhookEventLogStore {

    WebhookEventLog save(WebhookEventLog eventLog);

    /** @see BookingStore#saveAndFlush */
    WebhookEventLog saveAndFlush(WebhookEventLog eventLog);

    Optional<WebhookEventLog> findById(Long id);

    List<WebhookEventLog> findAll();

    boolean existsByProviderCodeAndEventId(String providerCode, String eventId);

    Optional<WebhookEventLog> findByProviderCodeAndEventId(String providerCode, String eventId);
}
