package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code Booking}.
 */
public record BookingId(String value) {

    public BookingId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("BookingId value must not be null or blank");
        }
    }

    public static BookingId of(String value) {
        return new BookingId(value);
    }

    public static BookingId newId() {
        return new BookingId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
