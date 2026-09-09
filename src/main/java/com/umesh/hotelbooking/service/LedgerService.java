package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.LedgerEntryResponse;
import com.umesh.hotelbooking.dto.LedgerViewResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.Direction;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.Refund;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.exception.RefundExceedsChargeException;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.LedgerEntryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Appends to the financial trail and enforces the one invariant it exists to protect (design
 * doc 9.4): {@code sum(REFUND) + sum(REVERSAL) <= sum(CHARGE)} per booking, checked before a
 * debit is written, never repaired after the fact.
 *
 * <p>Every {@code recordX} method takes the request's {@code correlationId} explicitly rather
 * than defaulting it to the payment/refund/reversal's own business uid (design doc 9.6:
 * {@code correlationId} threads through every audit record). Request-initiated writes get the
 * originating request's correlation id; {@code PaymentReconciliationService}, which has no
 * request in flight, generates one per sweep pass and reuses it across every entry that pass
 * writes.
 */
@Service
public class LedgerService {

    private final LedgerEntryStore store;
    private final BookingStore bookingStore;
    private final Clock clock;

    public LedgerService(LedgerEntryStore store, BookingStore bookingStore, Clock clock) {
        this.store = store;
        this.bookingStore = bookingStore;
        this.clock = clock;
    }

    /**
     * Called before any REFUND or REVERSAL is persisted — not just before its ledger entry —
     * so a violation aborts the whole operation with nothing written (design doc 9.5 step 4).
     */
    public void assertWithinInvariant(Booking booking, BigDecimal additionalDebit) {
        BigDecimal remaining = remainingBalance(booking.getId());
        if (additionalDebit.compareTo(remaining) > 0) {
            throw new RefundExceedsChargeException(booking.getBookingUid(), additionalDebit, remaining);
        }
    }

    public BigDecimal remainingBalance(Long bookingId) {
        BigDecimal charged = store.sumAmountByBookingIdAndType(bookingId, EntryType.CHARGE);
        BigDecimal refunded = store.sumAmountByBookingIdAndType(bookingId, EntryType.REFUND);
        BigDecimal reversed = store.sumAmountByBookingIdAndType(bookingId, EntryType.REVERSAL);
        return charged.subtract(refunded).subtract(reversed);
    }

    @Transactional
    public LedgerEntry recordCharge(Payment payment, Booking booking, String correlationId) {
        return append(booking.getId(), payment.getId(), EntryType.CHARGE, payment.getAmount(),
                payment.getCurrency(), Direction.CREDIT, payment.getProviderReference(), correlationId);
    }

    /** Caller must have already called {@link #assertWithinInvariant} for this amount. */
    @Transactional
    public LedgerEntry recordRefund(Refund refund, String correlationId) {
        return append(refund.getBookingId(), refund.getPaymentId(), EntryType.REFUND, refund.getAmount(),
                refund.getCurrency(), Direction.DEBIT, refund.getProviderReference(), correlationId);
    }

    /** Caller must have already called {@link #assertWithinInvariant} for this amount. */
    @Transactional
    public LedgerEntry recordReversal(Reversal reversal, String correlationId) {
        return append(reversal.getBookingId(), reversal.getPaymentId(), EntryType.REVERSAL, reversal.getAmount(),
                reversal.getCurrency(), Direction.DEBIT, reversal.getProviderReference(), correlationId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> findByBooking(Long bookingId) {
        return store.findByBookingIdOrderByOccurredAtAsc(bookingId);
    }

    /** GET /api/v1/admin/ledger?bookingUid=. */
    @Transactional(readOnly = true)
    public LedgerViewResponse viewByBookingUid(String bookingUid) {
        Booking booking = bookingStore.findByBookingUid(bookingUid)
                .orElseThrow(() -> new BookingNotFoundException(bookingUid));
        List<LedgerEntry> entries = findByBooking(booking.getId());

        BigDecimal charged = store.sumAmountByBookingIdAndType(booking.getId(), EntryType.CHARGE);
        BigDecimal refunded = store.sumAmountByBookingIdAndType(booking.getId(), EntryType.REFUND);
        BigDecimal reversed = store.sumAmountByBookingIdAndType(booking.getId(), EntryType.REVERSAL);

        return new LedgerViewResponse(
                bookingUid,
                entries.stream().map(LedgerEntryResponse::from).toList(),
                charged, refunded, reversed,
                charged.subtract(refunded).subtract(reversed));
    }

    private LedgerEntry append(Long bookingId, Long paymentId, EntryType type, BigDecimal amount, String currency,
                               Direction direction, String providerReference, String correlationId) {
        return store.save(LedgerEntry.builder()
                .bookingId(bookingId)
                .paymentId(paymentId)
                .type(type)
                .amount(amount)
                .currency(currency)
                .direction(direction)
                .providerReference(providerReference)
                .occurredAt(Instant.now(clock))
                .correlationId(correlationId)
                .build());
    }
}
