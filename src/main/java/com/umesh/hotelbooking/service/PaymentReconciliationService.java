package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.PaymentStatusCheckProperties;
import com.umesh.hotelbooking.dto.ReconciliationRunResponse;
import com.umesh.hotelbooking.dto.ResolveManualReviewRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.GatewayCheckStatus;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import com.umesh.hotelbooking.exception.InvalidPaymentStateException;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.exception.PaymentNotFoundException;
import com.umesh.hotelbooking.gateway.CircuitBreakerOpenException;
import com.umesh.hotelbooking.gateway.GatewayOutcome;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.GatewayUnavailableException;
import com.umesh.hotelbooking.gateway.PaymentCircuitBreaker;
import com.umesh.hotelbooking.gateway.PaymentGatewayClient;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PaymentStatusCheckRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gets stuck payments out of {@code PAYMENT_UNKNOWN} (design doc 7.6). Without this,
 * {@code PAYMENT_UNKNOWN} is a dead end and the circuit breaker's fallback has nowhere to go.
 *
 * <p>Three independent decision points, each on its own clock, deliberately not conflated
 * (7.6.2): the inventory-hold-window release (does the room stay held), the status-check
 * ladder (do we keep asking the gateway), and the auto-reversal deadline (when do we presume
 * failure). Each payment is resolved in its own transaction, mirroring
 * {@link BookingSweeper}: one poisoned payment must not roll back the whole pass.
 */
@Service
public class PaymentReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentStatusCheckRepository statusCheckRepository;
    private final BookingRepository bookingRepository;
    private final PaymentGatewayRouter router;
    private final PaymentGatewayClient gatewayClient;
    private final PaymentCircuitBreaker circuitBreaker;
    private final InventoryReservationService reservationService;
    private final PaymentStatusCheckProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentReconciliationService(PaymentRepository paymentRepository,
                                        PaymentStatusCheckRepository statusCheckRepository,
                                        BookingRepository bookingRepository,
                                        PaymentGatewayRouter router,
                                        PaymentGatewayClient gatewayClient,
                                        PaymentCircuitBreaker circuitBreaker,
                                        InventoryReservationService reservationService,
                                        PaymentStatusCheckProperties properties,
                                        TransactionTemplate transactionTemplate,
                                        Clock clock) {
        this.paymentRepository = paymentRepository;
        this.statusCheckRepository = statusCheckRepository;
        this.bookingRepository = bookingRepository;
        this.router = router;
        this.gatewayClient = gatewayClient;
        this.circuitBreaker = circuitBreaker;
        this.reservationService = reservationService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public ReconciliationRunResponse run() {
        Instant now = Instant.now(clock);
        int inventoryReleased = releasePastHoldWindow(now);

        int checked = 0, settled = 0, failed = 0, manualReview = 0, pending = 0, errors = 0;
        for (Payment due : paymentRepository.findByStateAndNextAttemptAtBefore(PaymentState.UNKNOWN, now)) {
            checked++;
            switch (reconcileOne(due.getId(), now)) {
                case SETTLED -> settled++;
                case FAILED -> failed++;
                case MANUAL_REVIEW -> manualReview++;
                case PENDING -> pending++;
                case ERROR -> errors++;
                case SKIPPED -> { /* resolved by another path between the scan and this attempt */ }
            }
        }

        if (checked > 0 || inventoryReleased > 0) {
            log.info("Reconciliation pass: checked={} settled={} failed={} manualReview={} "
                            + "pending={} errors={} inventoryReleased={}",
                    checked, settled, failed, manualReview, pending, errors, inventoryReleased);
        }
        return new ReconciliationRunResponse(checked, settled, failed, manualReview, pending, errors, inventoryReleased);
    }

    /**
     * The T+15m step of 7.6.1, on its own clock, independent of whether the ladder has even
     * run yet: the room returns to sale, but the booking stays PAYMENT_UNKNOWN — the customer
     * still sees "pending".
     */
    private int releasePastHoldWindow(Instant now) {
        int released = 0;
        for (Payment candidate : paymentRepository.findByStateAndInventoryReleasedFalse(PaymentState.UNKNOWN)) {
            if (candidate.isDueForInventoryRelease(now, properties.inventoryHoldWindow())) {
                boolean didRelease = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                    Payment payment = paymentRepository.findById(candidate.getId()).orElse(null);
                    if (payment == null || payment.isInventoryReleased() || payment.getState() != PaymentState.UNKNOWN) {
                        return false;
                    }
                    Booking booking = bookingRepository.findById(payment.getBookingId()).orElseThrow();
                    reservationService.release(booking.getRoomTypeId(), booking.nights(), booking.getUnits());
                    payment.setInventoryReleased(true);
                    return true;
                }));
                if (didRelease) {
                    released++;
                }
            }
        }
        return released;
    }

    private enum Outcome { SETTLED, FAILED, MANUAL_REVIEW, PENDING, ERROR, SKIPPED }

    private Outcome reconcileOne(Long paymentId, Instant now) {
        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findById(paymentId).orElse(null);
            if (payment == null || payment.getState() != PaymentState.UNKNOWN) {
                return Outcome.SKIPPED;
            }

            // Presumed failure takes precedence over the ladder: past the deadline, stop
            // asking and resolve, regardless of how many ladder attempts remain (7.6.2).
            if (payment.hasExceededDeadline(now, properties.autoReversalDeadline())) {
                return resolveTerminal(payment, PaymentState.FAILED, BookingState.PAYMENT_FAILED, now,
                        "auto-reversal deadline exceeded while PENDING; presuming failure");
            }

            int nextAttemptNo = payment.getAttemptNo() + 1;
            if (nextAttemptNo > properties.maxAttempts()) {
                payment.transitionTo(PaymentState.MANUAL_REVIEW);
                payment.setNextAttemptAt(null);
                log.warn("Payment {} exhausted the status-check ladder; moved to MANUAL_REVIEW",
                        payment.getPaymentUid());
                return Outcome.MANUAL_REVIEW;
            }

            Booking booking = bookingRepository.findById(payment.getBookingId()).orElseThrow();
            return pollGateway(payment, booking, nextAttemptNo, now);
        });
    }

    private Outcome pollGateway(Payment payment, Booking booking, int nextAttemptNo, Instant now) {
        GatewayCheckStatus checkStatus;
        String summary;
        try {
            PaymentGatewayProvider provider = router.routeByProviderCode(payment.getProviderCode());
            GatewayOutcome outcome = circuitBreaker.execute(
                    () -> gatewayClient.callStatus(provider, payment.getProviderReference()));
            checkStatus = switch (outcome) {
                case SETTLED -> GatewayCheckStatus.SETTLED;
                case FAILED -> GatewayCheckStatus.FAILED;
                case PENDING -> GatewayCheckStatus.PENDING;
            };
            summary = "gateway reported " + outcome;
        } catch (GatewayTimeoutException | CircuitBreakerOpenException | GatewayUnavailableException e) {
            checkStatus = GatewayCheckStatus.ERROR;
            summary = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        Outcome result;
        Instant nextAttemptAt = null;
        int loggedAttemptNo = payment.getAttemptNo();

        switch (checkStatus) {
            case SETTLED -> {
                boolean stillHeld = !payment.isInventoryReleased();
                payment.transitionTo(PaymentState.SETTLED);
                payment.setNextAttemptAt(null);
                if (stillHeld) {
                    booking.transitionTo(BookingState.CONFIRMED);
                    // ledger CHARGE entry: built in the ledger phase.
                } else {
                    booking.transitionTo(BookingState.REVERSED);
                    // reverse(LATE_SUCCESS_ON_EXPIRED_BOOKING): built in the ledger phase.
                }
                loggedAttemptNo = nextAttemptNo;
                result = Outcome.SETTLED;
            }
            case FAILED -> {
                payment.transitionTo(PaymentState.FAILED);
                payment.setNextAttemptAt(null);
                booking.transitionTo(BookingState.PAYMENT_FAILED);
                releaseIfHeld(payment, booking);
                loggedAttemptNo = nextAttemptNo;
                result = Outcome.FAILED;
            }
            case PENDING -> {
                // Only a genuine PENDING answer consumes the ladder's attempt budget (7.6.4).
                payment.setAttemptNo(nextAttemptNo);
                Duration delay = properties.delayForAttempt(nextAttemptNo + 1)
                        .orElse(properties.intervals().get(properties.intervals().size() - 1));
                nextAttemptAt = now.plus(jitter(delay));
                payment.setNextAttemptAt(nextAttemptAt);
                loggedAttemptNo = nextAttemptNo;
                result = Outcome.PENDING;
            }
            case ERROR -> {
                // Our call failed; this is not evidence about the transaction, so the attempt
                // budget is untouched — only the schedule advances (7.6.4).
                Duration delay = properties.delayForAttempt(payment.getAttemptNo() + 1)
                        .orElse(properties.intervals().get(0));
                nextAttemptAt = now.plus(jitter(delay));
                payment.setNextAttemptAt(nextAttemptAt);
                result = Outcome.ERROR;
            }
            default -> throw new IllegalStateException("Unreachable: " + checkStatus);
        }

        statusCheckRepository.save(PaymentStatusCheck.builder()
                .paymentId(payment.getId())
                .attemptNo(loggedAttemptNo)
                .checkedAt(now)
                .nextAttemptAt(nextAttemptAt)
                .gatewayStatus(checkStatus)
                .responseSummary(summary)
                .correlationId(payment.getPaymentUid())
                .build());

        return result;
    }

    private Outcome resolveTerminal(Payment payment, PaymentState paymentOutcome, BookingState bookingOutcome,
                                    Instant now, String reason) {
        Booking booking = bookingRepository.findById(payment.getBookingId()).orElseThrow();
        payment.transitionTo(paymentOutcome);
        payment.setNextAttemptAt(null);
        booking.transitionTo(bookingOutcome);
        releaseIfHeld(payment, booking);

        statusCheckRepository.save(PaymentStatusCheck.builder()
                .paymentId(payment.getId())
                .attemptNo(payment.getAttemptNo())
                .checkedAt(now)
                .gatewayStatus(GatewayCheckStatus.FAILED)
                .responseSummary(reason)
                .correlationId(payment.getPaymentUid())
                .build());

        log.warn("Payment {} resolved by deadline: {}", payment.getPaymentUid(), reason);
        return Outcome.FAILED;
    }

    private void releaseIfHeld(Payment payment, Booking booking) {
        if (!payment.isInventoryReleased()) {
            reservationService.release(booking.getRoomTypeId(), booking.nights(), booking.getUnits());
            payment.setInventoryReleased(true);
        }
    }

    /** Applies {@code payment.status-check.jitter-ratio} so many payments at the same attempt
     * number do not all poll the gateway in the same instant. */
    private Duration jitter(Duration base) {
        double ratio = properties.jitterRatio();
        double factor = 1 + ThreadLocalRandom.current().nextDouble(-ratio, ratio);
        return Duration.ofMillis(Math.max(0, (long) (base.toMillis() * factor)));
    }

    /** POST /api/v1/admin/payments/{id}/resolve — the human exit from MANUAL_REVIEW (7.6.3). */
    public PaymentResponse resolveManualReview(String paymentUid, ResolveManualReviewRequest request) {
        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByPaymentUid(paymentUid)
                    .orElseThrow(() -> new PaymentNotFoundException(paymentUid));
            if (payment.getState() != PaymentState.MANUAL_REVIEW) {
                throw new InvalidPaymentStateException(
                        "Payment " + paymentUid + " is not awaiting manual review (state=" + payment.getState() + ")");
            }
            if (request.outcome() != PaymentState.SETTLED && request.outcome() != PaymentState.FAILED) {
                throw new InvalidRequestException("outcome must be SETTLED or FAILED");
            }

            Booking booking = bookingRepository.findById(payment.getBookingId()).orElseThrow();
            payment.transitionTo(request.outcome());

            if (request.outcome() == PaymentState.SETTLED) {
                booking.transitionTo(payment.isInventoryReleased() ? BookingState.REVERSED : BookingState.CONFIRMED);
            } else {
                booking.transitionTo(BookingState.PAYMENT_FAILED);
                releaseIfHeld(payment, booking);
            }
            return PaymentResponse.from(payment, booking.getBookingUid());
        });
    }

    public List<PaymentResponse> listStuck() {
        return paymentRepository.findByStateIn(List.of(PaymentState.UNKNOWN, PaymentState.MANUAL_REVIEW)).stream()
                .map(payment -> PaymentResponse.from(payment,
                        bookingRepository.findById(payment.getBookingId()).map(Booking::getBookingUid).orElse(null)))
                .toList();
    }
}
