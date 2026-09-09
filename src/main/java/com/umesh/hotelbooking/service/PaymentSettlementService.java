package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.exception.PaymentNotFoundException;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place a payment's terminal outcome is applied to both the payment and its booking
 * (extracted from {@code PaymentReconciliationService} in Phase 7, §8.4 of that phase's task
 * spec). Before this extraction, the SETTLED case — record the charge, then confirm the
 * booking or reverse it depending on whether the inventory hold already lapsed — was written
 * out separately in the status-check ladder and in {@code resolveManualReview}; a webhook
 * handler would have been a third copy, and a third place for the three to drift apart.
 *
 * <p>Every caller — the ladder, admin manual-review resolution, and the inbound webhook path
 * — now goes through {@link #settle} or {@link #fail}.
 *
 * <p>Both methods are FSM-idempotent (design doc 8c): re-settling an already-{@code SETTLED}
 * payment, or re-failing an already-{@code FAILED} one, is a no-op. {@code
 * PaymentStateMachine} already treats a same-state transition as a permitted no-op, but that
 * alone is not enough here — without this explicit guard, a second call would still re-run
 * {@code recordCharge} and write a duplicate ledger entry even though the transition itself
 * did not throw. The guard is what a webhook needs: a provider can legitimately send two
 * different {@code eventId}s for the same settlement, so the {@code
 * (provider_code, event_id)} unique constraint alone cannot be the only thing standing
 * between one ledger entry and two.
 *
 * <p>{@link #settleByProviderReference} and {@link #failByProviderReference} exist
 * specifically for {@code InboundWebhookService}, which — unlike the status-check ladder and
 * {@code resolveManualReview}, both of which load {@code Payment}/{@code Booking} inside a
 * transaction they already hold open — has no ambient transaction of its own when it looks a
 * payment up. Loading the entities outside this class's own {@code @Transactional} boundary
 * and only then calling {@link #settle}/{@link #fail} would hand this class already-detached
 * entities: their mutations would never be flushed, since a detached instance has no
 * persistence context to be dirty-checked against. Looking them up <em>inside</em> the same
 * transactional method that mutates them is what keeps them attached.
 */
@Service
public class PaymentSettlementService {

    private final LedgerService ledgerService;
    private final ReversalService reversalService;
    private final InventoryReservationService reservationService;
    private final PaymentStore paymentStore;
    private final BookingStore bookingStore;

    public PaymentSettlementService(LedgerService ledgerService,
                                    ReversalService reversalService,
                                    InventoryReservationService reservationService,
                                    PaymentStore paymentStore,
                                    BookingStore bookingStore) {
        this.ledgerService = ledgerService;
        this.reversalService = reversalService;
        this.reservationService = reservationService;
        this.paymentStore = paymentStore;
        this.bookingStore = bookingStore;
    }

    /** For a caller with no {@code Payment}/{@code Booking} of its own in hand yet — see the
     * class Javadoc for why this must do its own lookup rather than receiving already-loaded
     * entities. */
    @Transactional
    public void settleByProviderReference(String providerReference, String correlationId) {
        Payment payment = findByProviderReference(providerReference);
        Booking booking = bookingStore.findById(payment.getBookingId()).orElseThrow();
        settle(payment, booking, correlationId);
    }

    /** @see #settleByProviderReference */
    @Transactional
    public void failByProviderReference(String providerReference) {
        Payment payment = findByProviderReference(providerReference);
        Booking booking = bookingStore.findById(payment.getBookingId()).orElseThrow();
        fail(payment, booking);
    }

    private Payment findByProviderReference(String providerReference) {
        return paymentStore.findByProviderReference(providerReference)
                .orElseThrow(() -> new PaymentNotFoundException(String.valueOf(providerReference)));
    }

    /**
     * Money moved. The charge is real regardless of what happens to the booking next, so it
     * is recorded before any reversal of it — the ledger invariant would otherwise reject
     * reversing a charge that was never written.
     */
    @Transactional
    public void settle(Payment payment, Booking booking, String correlationId) {
        if (payment.getState() == PaymentState.SETTLED) {
            return;
        }
        boolean stillHeld = !payment.isInventoryReleased();
        payment.transitionTo(PaymentState.SETTLED);
        payment.setNextAttemptAt(null);
        ledgerService.recordCharge(payment, booking, correlationId);
        if (stillHeld) {
            booking.transitionTo(BookingState.CONFIRMED);
        } else {
            reversalService.reverse(payment, booking, ReversalReason.LATE_SUCCESS_ON_EXPIRED_BOOKING, correlationId);
        }
    }

    /** The gateway declined, or we have given up waiting. Releases the room-nights if they are
     * still held; a settled charge is never involved on this path. */
    @Transactional
    public void fail(Payment payment, Booking booking) {
        if (payment.getState() == PaymentState.FAILED) {
            return;
        }
        payment.transitionTo(PaymentState.FAILED);
        payment.setNextAttemptAt(null);
        booking.transitionTo(BookingState.PAYMENT_FAILED);
        releaseIfHeld(payment, booking);
    }

    private void releaseIfHeld(Payment payment, Booking booking) {
        if (!payment.isInventoryReleased()) {
            reservationService.release(booking.getRoomTypeId(), booking.nights(), booking.getUnits());
            payment.setInventoryReleased(true);
        }
    }
}
