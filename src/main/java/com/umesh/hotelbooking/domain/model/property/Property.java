package com.umesh.hotelbooking.domain.model.property;

import com.umesh.hotelbooking.domain.vo.PropertyId;

/**
 * Placeholder for the Property aggregate. Phase 1 needs only enough of this type to give
 * {@code PropertyRepository} a concrete signature; the full model (name, location, star
 * rating, owning PropertyGroup, room types) is built in the onboarding phase.
 */
public final class Property {

    private final PropertyId id;

    public Property(PropertyId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
    }

    public PropertyId getId() {
        return id;
    }
}
