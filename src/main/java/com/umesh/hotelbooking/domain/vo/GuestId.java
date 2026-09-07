package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a guest. Referenced from {@code Booking} and elsewhere purely as an
 * opaque id — no guest personal data is ever carried alongside it in domain types, so that a
 * later erasure request can remove the guest's profile without touching financial or
 * booking history.
 */
public record GuestId(String value) {

    public GuestId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GuestId value must not be null or blank");
        }
    }

    public static GuestId of(String value) {
        return new GuestId(value);
    }

    public static GuestId newId() {
        return new GuestId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
