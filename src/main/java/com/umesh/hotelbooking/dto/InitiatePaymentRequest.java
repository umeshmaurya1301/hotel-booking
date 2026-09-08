package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;

/**
 * Pays for a booking (design doc 11, POST /api/v1/user/bookings/{id}/pay).
 *
 * @param msgId optional client-supplied idempotency key (design doc 8a). A retried request
 *     with the same msgId and the same body returns the original response rather than
 *     initiating a second payment attempt.
 * @param simulate demo-only lever with no real gateway behind it: picks what the mock
 *     provider does (settle, decline, time out, or get stuck pending). Omit for the happy
 *     path — the default is immediate settlement.
 */
public record InitiatePaymentRequest(
        String msgId,
        @NotNull PaymentMethod method,
        SimulatedOutcome simulate) {
}
