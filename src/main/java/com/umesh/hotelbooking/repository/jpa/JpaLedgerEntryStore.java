package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import com.umesh.hotelbooking.repository.LedgerEntryStore;

import java.math.BigDecimal;
import java.util.List;

/** JPA adapter for {@link LedgerEntryStore}. */
class JpaLedgerEntryStore implements LedgerEntryStore {

    private final JpaLedgerEntryRepository repository;

    JpaLedgerEntryStore(JpaLedgerEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public LedgerEntry save(LedgerEntry entry) {
        return repository.save(entry);
    }

    @Override
    public List<LedgerEntry> findByBookingIdOrderByOccurredAtAsc(Long bookingId) {
        return repository.findByBookingIdOrderByOccurredAtAsc(bookingId);
    }

    @Override
    public BigDecimal sumAmountByBookingIdAndType(Long bookingId, EntryType type) {
        return repository.sumAmountByBookingIdAndType(bookingId, type);
    }
}
