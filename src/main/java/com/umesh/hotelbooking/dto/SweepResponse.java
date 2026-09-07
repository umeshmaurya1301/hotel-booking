package com.umesh.hotelbooking.dto;

/**
 * Outcome of one sweeper pass (design doc 4.4).
 *
 * @param holdsExpired bookings whose hold lapsed; their room-nights were released
 * @param bookingsCompleted confirmed stays past checkout in the property's own timezone
 * @param skippedDueToConcurrentChange bookings another thread resolved first — a payment
 *     that settled while the sweep was running. Reported rather than hidden, because a
 *     persistently non-zero count says something real about contention.
 */
public record SweepResponse(int holdsExpired, int bookingsCompleted, int skippedDueToConcurrentChange) {
}
