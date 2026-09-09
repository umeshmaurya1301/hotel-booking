package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.webhook.WebhookEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaWebhookEventLogStore}. Not injected outside this
 * package. */
interface JpaWebhookEventLogRepository extends JpaRepository<WebhookEventLog, Long> {

    boolean existsByProviderCodeAndEventId(String providerCode, String eventId);

    Optional<WebhookEventLog> findByProviderCodeAndEventId(String providerCode, String eventId);
}
