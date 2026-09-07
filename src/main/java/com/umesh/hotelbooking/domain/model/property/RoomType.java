package com.umesh.hotelbooking.domain.model.property;

import com.umesh.hotelbooking.domain.vo.RoomTypeId;

/**
 * Placeholder for the RoomType aggregate. Phase 1 needs only enough of this type to give
 * {@code RoomTypeRepository} a concrete signature; capacity, amenities and base pricing are
 * built in the onboarding phase.
 */
public final class RoomType {

    private final RoomTypeId id;

    public RoomType(RoomTypeId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
    }

    public RoomTypeId getId() {
        return id;
    }
}
