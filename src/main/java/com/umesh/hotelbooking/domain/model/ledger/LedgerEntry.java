package com.umesh.hotelbooking.domain.model.ledger;

import com.umesh.hotelbooking.domain.vo.LedgerEntryId;

/**
 * Placeholder for the append-only ledger entry. Phase 1 needs only enough of this type to
 * give {@code LedgerEntryRepository} a concrete signature; the full record (booking/payment
 * references, entry type, amount, direction) is built in the ledger phase.
 */
public final class LedgerEntry {

    private final LedgerEntryId id;

    public LedgerEntry(LedgerEntryId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
    }

    public LedgerEntryId getId() {
        return id;
    }
}
