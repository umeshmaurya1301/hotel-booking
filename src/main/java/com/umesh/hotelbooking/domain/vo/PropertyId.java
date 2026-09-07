package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code Property} (a single hotel/building).
 */
public record PropertyId(String value) {

    public PropertyId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyId value must not be null or blank");
        }
    }

    public static PropertyId of(String value) {
        return new PropertyId(value);
    }

    public static PropertyId newId() {
        return new PropertyId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
