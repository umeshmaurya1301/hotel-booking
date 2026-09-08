package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.CancelBookingRequest;
import com.umesh.hotelbooking.dto.CancellationResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.Refund;
import com.umesh.hotelbooking.entity.RefundState;
import com.umesh.hotelbooking.event.BookingCancelledEvent;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.exception.InvalidPaymentStateException;
import com.umesh.hotelbooking.exception.InvalidStateTransitionException;
import com.umesh.hotelbooking.exception.PropertyNotFoundException;
import com.umesh.hotelbooking.gateway.GatewayOutcome;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.GatewayUnavailableException;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.gateway.RefundRequest;
import com.umesh.hotelbooking.gateway.RefundResult;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * Cancels a confirmed booking and works out what comes back (design doc 9.5). The step
 * numbers in comments below are the design document's own — this method is a direct
 * transcription of that ordering, not an approximation of it.
 */
@Service
public class CancellationService {

    private static final Logger log = LoggerFactory.getLogger(CancellationService.class);

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundPolicyFactory refundPolicyFactory;
    private final LedgerService ledgerService;
    private final InventoryReservationService reservationService;
    private final PaymentGatewayRouter router;
    private final IdempotencyService idempotencyService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancellationService(BookingRepository bookingRepository,
                               PropertyRepository propertyRepository,
                               PaymentRepository paymentRepository,
                               RefundRepository refundRepository,
                               RefundPolicyFactory refundPolicyFactory,
                               LedgerService ledgerService,
                               InventoryReservationService reservationService,
                               PaymentGatewayRouter router,
                               IdempotencyService idempotencyService,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundPolicyFactory = refundPolicyFactory;
        this.ledgerService = ledgerService;
        this.reservationService = reservationService;
        this.router = router;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public CancellationResponse cancel(String bookingUid, CancelBookingRequest request) {
        boolean hasMsgId = request.msgId() != null && !request.msgId().isBlank();
        if (hasMsgId) {
            // 1. Idempotency check on msgId.
            var cached = idempotencyService.begin(request.msgId(), request, CancellationResponse.class);
            if (cached.isPresent()) {
                return cached.get();
            }
        }

        Booking booking = bookingRepository.findByBookingUid(bookingUid)
                .orElseThrow(() -> new BookingNotFoundException(bookingUid));

        // 2. FSM validate: CONFIRMED -> CANCELLED (reject if already terminal).
        // Deliberately not BookingStateMachine.assertCanTransition: that permits CANCELLED ->
        // CANCELLED as a same-state no-op, which is right for an idempotent webhook replay
        // but wrong here, since a second call with no msgId would re-release inventory and
        // re-issue a refund. Only a booking currently CONFIRMED may be cancelled.
        if (booking.getState() != BookingState.CONFIRMED) {
            throw new InvalidStateTransitionException(booking.getState().name(), BookingState.CANCELLED.name());
        }

        Payment settledPayment = paymentRepository.findByBookingId(booking.getId()).stream()
                .filter(payment -> payment.getState() == PaymentState.SETTLED)
                .findFirst()
                .orElseThrow(() -> new InvalidPaymentStateException(
                        "Booking " + bookingUid + " has no settled payment to refund"));

        Property property = propertyRepository.findById(booking.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException("for booking " + bookingUid));
        String policyCode = property.getPropertyGroup().getRefundPolicyCode();

        // 3. RefundPolicy.calculate(booking, now) [Strategy].
        Instant now = Instant.now(clock);
        var policy = refundPolicyFactory.resolveOrDefault(policyCode);
        BigDecimal refundAmount = policy.calculate(booking, property.zone(), now);

        // 4. Assert ledger invariant: refunded + this <= charged.
        ledgerService.assertWithinInvariant(booking, refundAmount);

        // 5. Release inventory - decrement booked_units per night, under lock. Before the
        // gateway call, not after: the room becomes bookable immediately, and a subsequent
        // refund failure is a money problem to reconcile, not a reason to keep it blocked.
        reservationService.release(booking.getRoomTypeId(), booking.nights(), booking.getUnits());

        // 6. Persist Refund(REQUESTED), transition booking -> CANCELLED.
        Refund refund = refundRepository.save(Refund.builder()
                .bookingId(booking.getId())
                .paymentId(settledPayment.getId())
                .amount(refundAmount)
                .currency(booking.getCurrency())
                .policyCode(policy.policyCode())
                .requestedAt(now)
                .build());
        booking.transitionTo(BookingState.CANCELLED);

        processRefund(refund, settledPayment, refundAmount, bookingUid);

        CancellationResponse response = new CancellationResponse(
                booking.getBookingUid(), booking.getState(), refund.getRefundUid(),
                refundAmount, booking.getCurrency(), refund.getState());

        // 9. Publish BookingCancelledEvent [Observer].
        eventPublisher.publishEvent(new BookingCancelledEvent(
                booking.getBookingUid(), refund.getRefundUid(), refundAmount, policy.policyCode(), now));

        if (hasMsgId) {
            idempotencyService.complete(request.msgId(), response);
        }
        return response;
    }

    /**
     * 7. Gateway refund call — outside any lock (the reservation lock is already released by
     * this point), idempotent by {@code settledPayment.getProviderReference()}. 8. Append
     * LedgerEntry(REFUND, DEBIT), only once money has actually moved.
     *
     * <p>A zero-amount refund (a policy that grants nothing) skips the gateway and the ledger
     * entirely — there is nothing to move and nothing to record.
     */
    private void processRefund(Refund refund, Payment settledPayment, BigDecimal refundAmount, String bookingUid) {
        if (refundAmount.signum() == 0) {
            refund.transitionTo(RefundState.PROCESSING);
            refund.transitionTo(RefundState.COMPLETED);
            refund.setCompletedAt(Instant.now(clock));
            return;
        }

        refund.transitionTo(RefundState.PROCESSING);
        try {
            PaymentGatewayProvider provider = router.routeByProviderCode(settledPayment.getProviderCode());
            RefundResult result = provider.refund(new RefundRequest(
                    settledPayment.getProviderReference(), refundAmount, refund.getCurrency()));

            if (result.outcome() == GatewayOutcome.SETTLED) {
                refund.setProviderReference(result.refundReference());
                refund.transitionTo(RefundState.COMPLETED);
                refund.setCompletedAt(Instant.now(clock));
                ledgerService.recordRefund(refund);
            } else {
                refund.transitionTo(RefundState.FAILED);
                log.warn("Refund {} for booking {} was not accepted by the gateway: {}",
                        refund.getRefundUid(), bookingUid, result.message());
            }
        } catch (GatewayTimeoutException | GatewayUnavailableException e) {
            refund.transitionTo(RefundState.FAILED);
            log.warn("Refund {} for booking {} could not reach the gateway: {}",
                    refund.getRefundUid(), bookingUid, e.getMessage());
        }
    }
}
