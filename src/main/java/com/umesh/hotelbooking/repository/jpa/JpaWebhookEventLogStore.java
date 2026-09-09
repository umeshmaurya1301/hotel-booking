package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.repository.WebhookEventLogStore;
import com.umesh.hotelbooking.webhook.WebhookEventLog;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link WebhookEventLogStore}. */
class JpaWebhookEventLogStore implements WebhookEventLogStore {

    private final JpaWebhookEventLogRepository repository;

    JpaWebhookEventLogStore(JpaWebhookEventLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public WebhookEventLog save(WebhookEventLog eventLog) {
        return repository.save(eventLog);
    }

    @Override
    public WebhookEventLog saveAndFlush(WebhookEventLog eventLog) {
        return repository.saveAndFlush(eventLog);
    }

    @Override
    public Optional<WebhookEventLog> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<WebhookEventLog> findAll() {
        return repository.findAll();
    }

    @Override
    public boolean existsByProviderCodeAndEventId(String providerCode, String eventId) {
        return repository.existsByProviderCodeAndEventId(providerCode, eventId);
    }

    @Override
    public Optional<WebhookEventLog> findByProviderCodeAndEventId(String providerCode, String eventId) {
        return repository.findByProviderCodeAndEventId(providerCode, eventId);
    }
}
