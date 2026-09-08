package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Direction;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        String ledgerEntryUid,
        EntryType type,
        BigDecimal amount,
        String currency,
        Direction direction,
        String providerReference,
        Instant occurredAt,
        String correlationId) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getLedgerEntryUid(), entry.getType(), entry.getAmount(),
                entry.getCurrency(), entry.getDirection(), entry.getProviderReference(),
                entry.getOccurredAt(), entry.getCorrelationId());
    }
}
