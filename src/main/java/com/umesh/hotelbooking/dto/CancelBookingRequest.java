package com.umesh.hotelbooking.dto;

/**
 * Cancels a confirmed booking (design doc 9.5, POST /api/v1/user/bookings/{id}/cancel).
 *
 * @param msgId optional client-supplied idempotency key (design doc 8a) — a retried cancel
 *     with the same msgId and body returns the original response rather than releasing
 *     inventory or issuing a refund twice
 * @param reason optional, guest-supplied free text; not interpreted by the system
 */
public record CancelBookingRequest(String msgId, String reason) {
}
