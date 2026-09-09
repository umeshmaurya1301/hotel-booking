package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.entity.ReversalState;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.exception.InvalidPaymentStateException;
import com.umesh.hotelbooking.gateway.GatewayOutcome;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.GatewayUnavailableException;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.gateway.ReversalRequest;
import com.umesh.hotelbooking.gateway.ReversalResult;
import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.ReversalStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Undoes a settled payment because the transaction should not have stood (design doc 9.1,
 * 9.2). This is the single place every reversal scenario — late settlement after a hold
 * lapsed, a duplicate charge, an admin correction — goes through, so the booking transition,
 * the gateway call and the ledger entry can never happen in only two of the three.
 *
 * <p>The booking moves to {@code REVERSED} regardless of whether the gateway's own reversal
 * call succeeds. That mirrors {@code CancellationService}'s philosophy: the booking's fate is
 * a business decision separate from whether money has actually moved back yet, and a gateway
 * failure here is a reconciliation problem the {@link Reversal} record's own {@code FAILED}
 * state flags, not a reason to leave the booking in limbo.
 */
@Service
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);

    private final ReversalStore reversalStore;
    private final BookingStore bookingStore;
    private final PaymentStore paymentStore;
    private final PaymentGatewayRouter router;
    private final LedgerService ledgerService;
    private final Clock clock;

    public ReversalService(ReversalStore reversalStore,
                           BookingStore bookingStore,
                           PaymentStore paymentStore,
                           PaymentGatewayRouter router,
                           LedgerService ledgerService,
                           Clock clock) {
        this.reversalStore = reversalStore;
        this.bookingStore = bookingStore;
        this.paymentStore = paymentStore;
        this.router = router;
        this.ledgerService = ledgerService;
        this.clock = clock;
    }

    /**
     * POST /api/v1/admin/bookings/{id}/reverse — the admin entry point for
     * {@link ReversalReason#MANUAL_CORRECTION} and, structurally, {@link
     * ReversalReason#DUPLICATE_CHARGE}: an operator reversing a booking whose payment already
     * settled normally, discovered after the fact rather than caught by automation.
     */
    @Transactional
    public Reversal reverseBooking(String bookingUid, ReversalReason reason, String correlationId) {
        Booking booking = bookingStore.findByBookingUid(bookingUid)
                .orElseThrow(() -> new BookingNotFoundException(bookingUid));
        Payment settledPayment = paymentStore.findByBookingId(booking.getId()).stream()
                .filter(payment -> payment.getState() == PaymentState.SETTLED)
                .findFirst()
                .orElseThrow(() -> new InvalidPaymentStateException(
                        "Booking " + bookingUid + " has no settled payment to reverse"));
        return reverse(settledPayment, booking, reason, correlationId);
    }

    @Transactional
    public Reversal reverse(Payment payment, Booking booking, ReversalReason reason, String correlationId) {
        ledgerService.assertWithinInvariant(booking, payment.getAmount());

        Reversal reversal = reversalStore.save(Reversal.builder()
                .bookingId(booking.getId())
                .paymentId(payment.getId())
                .reason(reason)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .occurredAt(Instant.now(clock))
                .build());

        try {
            PaymentGatewayProvider provider = router.routeByProviderCode(payment.getProviderCode());
            ReversalResult result = provider.reverse(new ReversalRequest(
                    payment.getProviderReference(), payment.getAmount(), payment.getCurrency(), reason.name()));

            if (result.outcome() == GatewayOutcome.SETTLED) {
                reversal.setProviderReference(result.reversalReference());
                reversal.transitionTo(ReversalState.COMPLETED);
                ledgerService.recordReversal(reversal, correlationId);
            } else {
                reversal.transitionTo(ReversalState.FAILED);
                log.warn("Reversal {} for booking {} was not accepted by the gateway: {}",
                        reversal.getReversalUid(), booking.getBookingUid(), result.message());
            }
        } catch (GatewayTimeoutException | GatewayUnavailableException e) {
            reversal.transitionTo(ReversalState.FAILED);
            log.warn("Reversal {} for booking {} could not reach the gateway: {}",
                    reversal.getReversalUid(), booking.getBookingUid(), e.getMessage());
        }

        booking.transitionTo(BookingState.REVERSED);
        return reversal;
    }
}
