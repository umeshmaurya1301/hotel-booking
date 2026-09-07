package com.umesh.hotelbooking.event;

import java.time.Instant;

/**
 * Published when a confirmed stay passes its checkout date in the property's own timezone.
 *
 * <p>This is what makes CONFIRMED -> COMPLETED reachable. A state in the transition table
 * that nothing can ever reach is a defect, not an unused feature.
 */
public record BookingCompletedEvent(
        String bookingUid,
        Instant occurredAt) {
}
