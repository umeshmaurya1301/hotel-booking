package com.umesh.hotelbooking.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/admin/ledger?bookingUid=. {@code balance} is derived here from the entries at
 * read time, never stored — the whole point of design doc 9.4's append-only design is that
 * this number always has an answer to "why is it what it is" in the entries above it.
 */
public record LedgerViewResponse(
        String bookingUid,
        List<LedgerEntryResponse> entries,
        BigDecimal totalCharged,
        BigDecimal totalRefunded,
        BigDecimal totalReversed,
        BigDecimal balance) {
}
