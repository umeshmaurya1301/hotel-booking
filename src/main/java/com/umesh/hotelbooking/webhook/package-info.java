/**
 * Inbound and outbound webhook handling (design doc 12.1–12.4, 8c): the shared envelope,
 * {@code webhook_event_log} and its repository, the verification/dedupe/processing sequence
 * in {@link com.umesh.hotelbooking.webhook.InboundWebhookService}, and signed outbound
 * delivery in {@link com.umesh.hotelbooking.webhook.WebhookDispatcher}.
 */
package com.umesh.hotelbooking.webhook;
