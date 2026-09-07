package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.ledger.LedgerEntry;
import com.umesh.hotelbooking.domain.vo.LedgerEntryId;

import java.util.Optional;

/**
 * Persistence port for {@link LedgerEntry}. Minimal for Phase 1; the ledger phase extends
 * this with lookups by booking for balance-invariant checks and admin ledger views. The
 * ledger is append-only, so this port deliberately has no update or delete operation.
 */
public interface LedgerEntryRepository {

    LedgerEntry save(LedgerEntry entry);

    Optional<LedgerEntry> findById(LedgerEntryId id);
}
