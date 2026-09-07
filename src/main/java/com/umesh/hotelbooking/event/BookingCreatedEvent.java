package com.umesh.hotelbooking.event;

import java.time.Instant;

/**
 * Published when a booking is created and its room-nights are held (design doc 4.4). The
 * hold is not yet paid for; {@code holdExpiresAt} is when it lapses if nothing else happens.
 */
public record BookingCreatedEvent(
        String bookingUid,
        String propertyUid,
        String roomTypeUid,
        int units,
        int nights,
        Instant holdExpiresAt,
        Instant occurredAt) {
}
