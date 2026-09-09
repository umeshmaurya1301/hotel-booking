package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

/** Spring Data JPA queries behind {@link JpaLedgerEntryStore}. Not injected outside this
 * package. */
interface JpaLedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByBookingIdOrderByOccurredAtAsc(Long bookingId);

    /** A database aggregate, not fetched-and-summed in Java — the port's stated requirement. */
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l where l.bookingId = :bookingId and l.type = :type")
    BigDecimal sumAmountByBookingIdAndType(@Param("bookingId") Long bookingId, @Param("type") EntryType type);
}
