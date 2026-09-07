package com.umesh.hotelbooking.event;

import java.time.Instant;

/**
 * Published once a property, its room types and its opening inventory horizon are all
 * committed (design doc 4.3, step 5).
 *
 * <p>Carries business uids rather than database ids: a listener — and, later, a consumer on
 * the other side of a topic — has no business knowing the internal primary keys.
 */
public record PropertyOnboardedEvent(
        String propertyUid,
        String propertyGroupUid,
        String city,
        int roomTypeCount,
        int nightsMaterialised,
        Instant occurredAt) {
}
