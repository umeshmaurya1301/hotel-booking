package com.umesh.hotelbooking.domain.model.guest;

import com.umesh.hotelbooking.domain.vo.GuestId;

/**
 * Placeholder for the Guest aggregate. Phase 1 needs only enough of this type to give
 * {@code GuestRepository} a concrete signature; guest profile data is built in a later phase
 * and, per the design's PII policy, is never referenced from {@code Booking} directly.
 */
public final class Guest {

    private final GuestId id;

    public Guest(GuestId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
    }

    public GuestId getId() {
        return id;
    }
}
