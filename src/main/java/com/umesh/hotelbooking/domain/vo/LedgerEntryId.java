package com.umesh.hotelbooking.domain.vo;

import java.util.UUID;

/**
 * Typed identifier for a {@code LedgerEntry}.
 */
public record LedgerEntryId(String value) {

    public LedgerEntryId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LedgerEntryId value must not be null or blank");
        }
    }

    public static LedgerEntryId of(String value) {
        return new LedgerEntryId(value);
    }

    public static LedgerEntryId newId() {
        return new LedgerEntryId(UUID.randomUUID().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
