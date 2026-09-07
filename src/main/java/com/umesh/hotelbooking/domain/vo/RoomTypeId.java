package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code RoomType} within a property (e.g. "Deluxe King").
 */
public record RoomTypeId(String value) {

    public RoomTypeId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RoomTypeId value must not be null or blank");
        }
    }

    public static RoomTypeId of(String value) {
        return new RoomTypeId(value);
    }

    public static RoomTypeId newId() {
        return new RoomTypeId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
