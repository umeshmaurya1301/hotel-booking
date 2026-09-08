package com.umesh.hotelbooking.webhook;

/**
 * The response body for every inbound webhook call (design doc 12.1, task spec §11.1). This
 * is a provider's own contract, not ours — it is deliberately never wrapped in {@code
 * ApiResponse} (see {@code ResponseEnvelopeAdvice}'s {@code basePackages} exclusion of {@code
 * controller.webhook}).
 *
 * @param status one of {@code RECEIVED}, {@code DUPLICATE} or {@code REJECTED}. Not an enum:
 *     this is a wire contract for an external caller, and a free-form string here costs
 *     nothing while an enum would invite the same "must the provider understand our Java
 *     type" question the envelope's own {@code eventType} already answered against.
 */
public record WebhookAck(String eventId, String status) {
}
