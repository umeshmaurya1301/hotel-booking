package com.umesh.hotelbooking.webhook;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Append-only, like every other audit table: no update or delete beyond what {@code
 * JpaRepository} exposes. */
public interface WebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {

    boolean existsByProviderCodeAndEventId(String providerCode, String eventId);

    Optional<WebhookEventLog> findByProviderCodeAndEventId(String providerCode, String eventId);
}
