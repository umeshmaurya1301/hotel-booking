package com.umesh.hotelbooking.event;

import java.time.Instant;

/**
 * Published when a hold lapses and its room-nights go back on sale (design doc 4.4).
 *
 * <p>The ordinary abandonment case — a guest created a booking and never paid — rather than
 * the stuck-payment path, which reaches its own states in a later phase.
 */
public record BookingExpiredEvent(
        String bookingUid,
        String roomTypeUid,
        int units,
        int nightsReleased,
        Instant occurredAt) {
}
