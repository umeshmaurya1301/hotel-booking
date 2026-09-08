package com.umesh.hotelbooking.gateway;

/**
 * @param providerPayload the raw, <b>unredacted</b> provider response — synthetic PAN-, CVV-
 *     and contact-shaped fields on the mock providers (design doc 12.6.6). Callers that
 *     persist this anywhere (a {@code payment_status_check} row, a webhook log) must run it
 *     through {@code PayloadRedactor} first; nothing in the {@code gateway} package redacts
 *     on its own, since the gateway boundary is exactly where the raw shape needs to exist
 *     for the redaction path to have something real to prove itself against.
 */
public record PaymentResult(GatewayOutcome outcome, String providerReference, String message, String providerPayload) {
}
