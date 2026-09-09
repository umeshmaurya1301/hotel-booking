package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.FieldEncryptionConfig;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.Refund;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.exception.RefundExceedsChargeException;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.LedgerEntryStore;
import com.umesh.hotelbooking.repository.jpa.JpaStores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 15.1 test 8, design doc 9.4's invariant: {@code sum(REFUND) + sum(REVERSAL) <= sum(CHARGE)}
 * per booking, checked before a debit is written, never repaired after the fact.
 *
 * <p>{@code @DataJpaTest} gives a real {@link LedgerEntryStore} against real H2, so the
 * running-total query ({@code sumAmountByBookingIdAndType}) is genuinely exercised rather than
 * stubbed — a mocked repository could not distinguish a real accumulating balance check from a
 * bug that only ever compares against the single most recent refund. {@code Booking} and
 * {@code Payment} are built in memory, never persisted: {@code LedgerEntry.bookingId} and
 * {@code .paymentId} are plain columns with no FK relation (by design - see that entity's own
 * Javadoc), so nothing here needs a real booking or payment row to exist.
 */
@DataJpaTest
@Import({FieldEncryptionConfig.class, JpaStores.class})
class RefundInvariantTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private LedgerEntryStore ledgerEntryStore;
    @Autowired
    private BookingStore bookingStore;

    private LedgerService ledgerService;

    private LedgerService ledgerService() {
        if (ledgerService == null) {
            ledgerService = new LedgerService(ledgerEntryStore, bookingStore, CLOCK);
        }
        return ledgerService;
    }

    private Booking booking(long id) {
        return Booking.builder().id(id).bookingUid("booking-" + id).totalAmount(new BigDecimal("1000.00"))
                .currency("INR").build();
    }

    private Payment payment(long id, long bookingId, BigDecimal amount) {
        return Payment.builder().id(id).bookingId(bookingId).amount(amount).currency("INR")
                .providerReference("ref-" + id).build();
    }

    private Refund refund(long bookingId, long paymentId, BigDecimal amount) {
        return Refund.builder().bookingId(bookingId).paymentId(paymentId).amount(amount).currency("INR")
                .providerReference("rfnd-" + paymentId).build();
    }

    @Test
    void aRefundWithinTheRemainingBalanceSucceedsAndAppendsAnEntry() {
        Booking booking = booking(1L);
        ledgerService().recordCharge(payment(1L, 1L, new BigDecimal("1000.00")), booking, "corr-1");

        ledgerService().assertWithinInvariant(booking, new BigDecimal("400.00"));
        ledgerService().recordRefund(refund(1L, 1L, new BigDecimal("400.00")), "corr-1");

        assertThat(ledgerEntryStore.findByBookingIdOrderByOccurredAtAsc(1L))
                .extracting(entry -> entry.getType())
                .containsExactly(EntryType.CHARGE, EntryType.REFUND);
    }

    @Test
    void aRefundExceedingTheRemainingBalanceThrowsAndWritesNothing() {
        Booking booking = booking(2L);
        ledgerService().recordCharge(payment(2L, 2L, new BigDecimal("1000.00")), booking, "corr-2");

        assertThatThrownBy(() -> ledgerService().assertWithinInvariant(booking, new BigDecimal("1200.00")))
                .isInstanceOf(RefundExceedsChargeException.class);

        assertThat(ledgerEntryStore.findByBookingIdOrderByOccurredAtAsc(2L))
                .as("rejected must mean nothing was written, not just that the amount was wrong")
                .extracting(entry -> entry.getType())
                .containsExactly(EntryType.CHARGE);
    }

    /**
     * A single-shot test cannot distinguish a real running-total check from a
     * compare-against-one-refund bug: two partial refunds that together exceed the charge.
     */
    @Test
    void twoPartialRefundsTheSecondPushingPastTheChargeIsRejectedWhileTheFirstStands() {
        Booking booking = booking(3L);
        ledgerService().recordCharge(payment(3L, 3L, new BigDecimal("1000.00")), booking, "corr-3");

        ledgerService().assertWithinInvariant(booking, new BigDecimal("600.00"));
        ledgerService().recordRefund(refund(3L, 3L, new BigDecimal("600.00")), "corr-3");

        // Remaining is now 400.00; a compare-against-only-the-latest-refund bug would wrongly
        // allow this against the original 1000.00 charge.
        assertThatThrownBy(() -> ledgerService().assertWithinInvariant(booking, new BigDecimal("500.00")))
                .isInstanceOf(RefundExceedsChargeException.class);

        assertThat(ledgerEntryStore.findByBookingIdOrderByOccurredAtAsc(3L))
                .extracting(entry -> entry.getType())
                .containsExactly(EntryType.CHARGE, EntryType.REFUND);
    }

    /** A reversal debits the same running total as a refund - the invariant is per booking, not per entry type. */
    @Test
    void aReversalCountsAgainstTheSameRunningTotalAsARefund() {
        Booking booking = booking(4L);
        ledgerService().recordCharge(payment(4L, 4L, new BigDecimal("1000.00")), booking, "corr-4");
        ledgerService().assertWithinInvariant(booking, new BigDecimal("1000.00"));
        Reversal reversal = Reversal.builder().bookingId(4L).paymentId(4L).amount(new BigDecimal("1000.00"))
                .currency("INR").providerReference("rvrs-4").build();
        ledgerService().recordReversal(reversal, "corr-4");

        assertThatThrownBy(() -> ledgerService().assertWithinInvariant(booking, new BigDecimal("0.01")))
                .as("nothing is left to refund once a full reversal has already been written")
                .isInstanceOf(RefundExceedsChargeException.class);
    }
}
