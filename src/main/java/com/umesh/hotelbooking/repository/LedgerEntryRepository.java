package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Append-only: no update or delete beyond what {@code JpaRepository} exposes. */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByLedgerEntryUid(String ledgerEntryUid);

    List<LedgerEntry> findByBookingIdOrderByOccurredAtAsc(Long bookingId);

    /**
     * The invariant check of design doc 9.4 needs this sum, not the individual rows — kept
     * as a database aggregate rather than fetched-and-summed in Java so it scales with
     * ledger history instead of with however many entries one booking has accumulated.
     */
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l where l.bookingId = :bookingId and l.type = :type")
    BigDecimal sumAmountByBookingIdAndType(@Param("bookingId") Long bookingId, @Param("type") EntryType type);
}
