package com.umesh.hotelbooking.dto;

/**
 * Cancels a confirmed booking (design doc 9.5, POST /api/v1/user/bookings/{id}/cancel).
 *
 * <p>Idempotency (design doc 8a) is carried by the enclosing {@link ApiRequest#msgId()}, not a
 * field here — there is deliberately no per-endpoint idempotency key.
 *
 * @param reason optional, guest-supplied free text; not interpreted by the system
 */
public record CancelBookingRequest(String reason) {
}
