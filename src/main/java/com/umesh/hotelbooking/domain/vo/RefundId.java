package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code Refund}.
 */
public record RefundId(String value) {

    public RefundId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RefundId value must not be null or blank");
        }
    }

    public static RefundId of(String value) {
        return new RefundId(value);
    }

    public static RefundId newId() {
        return new RefundId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
