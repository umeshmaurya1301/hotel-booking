package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import jakarta.validation.constraints.NotNull;

/**
 * Pays for a booking (design doc 11, POST /api/v1/user/bookings/{id}/pay).
 *
 * <p>Idempotency (design doc 8a) is carried by the enclosing {@link ApiRequest#msgId()}, not a
 * field here — there is deliberately no per-endpoint idempotency key.
 *
 * @param simulate demo-only lever with no real gateway behind it: picks what the mock
 *     provider does (settle, decline, time out, or get stuck pending). Omit for the happy
 *     path — the default is immediate settlement.
 */
public record InitiatePaymentRequest(
        @NotNull PaymentMethod method,
        SimulatedOutcome simulate) {
}
