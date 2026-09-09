package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;

/** Persistence port for {@link LedgerEntry}. Append-only: the port deliberately declares no
 * update or delete, so "the ledger is never rewritten" is a property of the contract rather
 * than a rule the callers are trusted to remember. */
public interface LedgerEntryStore {

    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByBookingIdOrderByOccurredAtAsc(Long bookingId);

    /**
     * The invariant check of design doc 9.4 needs this sum, not the individual rows. Declared as
     * an aggregate on the port rather than left to the caller so that it scales with ledger
     * history instead of with however many entries one booking has accumulated — a store that
     * satisfied this by loading every row and summing in memory would be honouring the signature
     * and missing the point.
     */
    BigDecimal sumAmountByBookingIdAndType(Long bookingId, EntryType type);
}
