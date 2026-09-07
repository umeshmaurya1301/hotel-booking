package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for an {@code Owner} account. A distinct type per identifier kind is what
 * makes it impossible to compile a call with two id arguments swapped.
 */
public record OwnerId(String value) {

    public OwnerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OwnerId value must not be null or blank");
        }
    }

    public static OwnerId of(String value) {
        return new OwnerId(value);
    }

    public static OwnerId newId() {
        return new OwnerId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
