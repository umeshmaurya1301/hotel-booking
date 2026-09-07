package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code Payment}.
 */
public record PaymentId(String value) {

    public PaymentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PaymentId value must not be null or blank");
        }
    }

    public static PaymentId of(String value) {
        return new PaymentId(value);
    }

    public static PaymentId newId() {
        return new PaymentId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
