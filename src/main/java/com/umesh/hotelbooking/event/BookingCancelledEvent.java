package com.umesh.hotelbooking.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published once a cancellation and its refund decision are committed (design doc 9.5 step 9). */
public record BookingCancelledEvent(
        String bookingUid,
        String refundUid,
        BigDecimal refundAmount,
        String refundPolicyCode,
        Instant occurredAt) {
}
