package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code PropertyGroup} (e.g. a hotel chain, or the single-property
 * group created for an independent owner).
 */
public record PropertyGroupId(String value) {

    public PropertyGroupId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PropertyGroupId value must not be null or blank");
        }
    }

    public static PropertyGroupId of(String value) {
        return new PropertyGroupId(value);
    }

    public static PropertyGroupId newId() {
        return new PropertyGroupId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
