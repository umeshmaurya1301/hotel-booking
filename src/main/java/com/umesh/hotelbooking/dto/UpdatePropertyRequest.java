package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Set;

/**
 * Partial update of a property's descriptive fields. Every field is optional; a null field
 * means "leave unchanged", which is what makes this a PATCH rather than a PUT.
 *
 * <p>Deliberately cannot change {@code zoneId} or {@code currency}: both are baked into
 * already-materialised inventory rows and already-taken bookings, so changing either would
 * silently reinterpret existing data rather than update it.
 */
public record UpdatePropertyRequest(
        String name,
        String locality,
        @Min(1) @Max(5) Integer starRating,
        Set<Amenity> amenities) {
}
