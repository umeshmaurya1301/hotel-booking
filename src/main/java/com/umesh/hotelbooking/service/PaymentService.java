package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.PaymentStatusCheckProperties;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.exception.InvalidPaymentStateException;
import com.umesh.hotelbooking.exception.PaymentNotFoundException;
import com.umesh.hotelbooking.exception.PropertyNotFoundException;
import com.umesh.hotelbooking.gateway.CircuitBreakerOpenException;
import com.umesh.hotelbooking.gateway.GatewayOutcome;
import com.umesh.hotelbooking.gateway.GatewayTimeoutException;
import com.umesh.hotelbooking.gateway.PaymentGatewayClient;
import com.umesh.hotelbooking.gateway.PaymentGatewayProvider;
import com.umesh.hotelbooking.gateway.PaymentGatewayRouter;
import com.umesh.hotelbooking.gateway.PaymentResult;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.web.RequestMeta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.umesh.hotelbooking.gateway.PaymentCircuitBreaker;
import com.umesh.hotelbooking.gateway.PaymentRequest;

/**
 * Initiates payment for a booking. This is where the payment-ambiguity path starts: a
 * breaker-open or timed-out call is never guessed at, and settles into {@code PAYMENT_UNKNOWN}
 * for {@link PaymentReconciliationService} to resolve later (design doc 7.2).
 */
@Service
public class PaymentService {

    private static final Set<PaymentState> ACTIVE_ATTEMPT_STATES =
            Set.of(PaymentState.INITIATED, PaymentState.PROCESSING, PaymentState.UNKNOWN, PaymentState.MANUAL_REVIEW);

    private final BookingRepository bookingRepository;
    private final PropertyRepository propertyRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRouter router;
    private final PaymentGatewayClient gatewayClient;
    private final PaymentCircuitBreaker circuitBreaker;
    private final PaymentStatusCheckProperties statusCheckProperties;
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;
    private final Clock clock;

    public PaymentService(BookingRepository bookingRepository,
                          PropertyRepository propertyRepository,
                          PaymentRepository paymentRepository,
                          PaymentGatewayRouter router,
                          PaymentGatewayClient gatewayClient,
                          PaymentCircuitBreaker circuitBreaker,
                          PaymentStatusCheckProperties statusCheckProperties,
                          IdempotencyService idempotencyService,
                          LedgerService ledgerService,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.propertyRepository = propertyRepository;
        this.paymentRepository = paymentRepository;
        this.router = router;
        this.gatewayClient = gatewayClient;
        this.circuitBreaker = circuitBreaker;
        this.statusCheckProperties = statusCheckProperties;
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse pay(String bookingUid, RequestMeta meta, InitiatePaymentRequest request) {
        var cached = idempotencyService.begin(meta, request, PaymentResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Booking booking = bookingRepository.findByBookingUid(bookingUid)
                .orElseThrow(() -> new BookingNotFoundException(bookingUid));
        requirePayableBookingState(booking);
        Payment payment = resolvePaymentAttempt(booking, request.method());

        PaymentResponse response;
        if (payment.getState() == PaymentState.UNKNOWN || payment.getState() == PaymentState.MANUAL_REVIEW) {
            // Already being resolved by reconciliation or a human. A retried "pay" call
            // reports current status rather than touching the gateway a second time.
            response = PaymentResponse.from(payment, booking.getBookingUid());
        } else {
            response = attemptGatewayCall(booking, payment, request, meta.correlationId());
        }

        idempotencyService.complete(meta.msgId(), response);
        return response;
    }

    private PaymentResponse attemptGatewayCall(Booking booking, Payment payment, InitiatePaymentRequest request,
                                               String correlationId) {
        Property property = propertyRepository.findById(booking.getPropertyId())
                .orElseThrow(() -> new PropertyNotFoundException("for booking " + booking.getBookingUid()));
        String bankCode = property.getPropertyGroup().getSettlementBankCode();

        PaymentGatewayProvider provider = router.route(request.method(), bankCode);
        payment.setProviderCode(provider.providerCode());
        payment.setBankCode(bankCode);
        if (payment.getState() == PaymentState.INITIATED) {
            payment.transitionTo(PaymentState.PROCESSING);
        }
        if (booking.getState() == BookingState.CREATED) {
            booking.transitionTo(BookingState.PENDING_PAYMENT);
        }
        Payment saved = paymentRepository.save(payment);

        PaymentRequest gatewayRequest = new PaymentRequest(
                saved.getProviderReference(), request.method(), bankCode,
                saved.getAmount(), saved.getCurrency(), request.simulate());

        try {
            PaymentResult result = circuitBreaker.execute(() -> gatewayClient.callInitiate(provider, gatewayRequest));
            applyOutcome(booking, saved, result.outcome(), correlationId);
        } catch (GatewayTimeoutException | CircuitBreakerOpenException e) {
            // Breaker open OR timeout: the outcome is unobserved, not failed. Never CONFIRMED
            // (money may not have moved), never FAILED (money may have moved) (7.2).
            markUnknown(booking, saved);
        }

        return PaymentResponse.from(saved, booking.getBookingUid());
    }

    @Transactional(readOnly = true)
    public PaymentResponse find(String paymentUid) {
        Payment payment = paymentRepository.findByPaymentUid(paymentUid)
                .orElseThrow(() -> new PaymentNotFoundException(paymentUid));
        String bookingUid = bookingRepository.findById(payment.getBookingId())
                .map(Booking::getBookingUid).orElse(null);
        return PaymentResponse.from(payment, bookingUid);
    }

    /**
     * A booking's own transition table only allows {@code PAYMENT_FAILED -> EXPIRED} (design
     * doc 2.7) — there is deliberately no path back from a failed payment to another attempt
     * on the <em>same</em> booking. A decline means this booking is done; the guest books
     * again. Only {@code CREATED}, {@code PENDING_PAYMENT} (a fresh or retried gateway call),
     * {@code PAYMENT_UNKNOWN} and {@code MANUAL_REVIEW} (status-only, handled by the caller)
     * may reach the gateway path here; every other state is rejected before it gets there,
     * rather than surfacing as a confusing state-machine error from deep inside the flow.
     */
    private void requirePayableBookingState(Booking booking) {
        boolean payable = switch (booking.getState()) {
            case CREATED, PENDING_PAYMENT, PAYMENT_UNKNOWN, MANUAL_REVIEW -> true;
            case CONFIRMED, CANCELLED, COMPLETED, EXPIRED, PAYMENT_FAILED, REVERSED -> false;
        };
        if (!payable) {
            throw new InvalidPaymentStateException(
                    "Booking " + booking.getBookingUid() + " cannot be paid in state " + booking.getState());
        }
    }

    /**
     * A booking may have a history of payment attempts (a decline is retryable). This decides
     * whether to reuse an in-flight/unresolved attempt, reject outright, or start a new one.
     */
    private Payment resolvePaymentAttempt(Booking booking, PaymentMethod method) {
        List<Payment> existing = paymentRepository.findByBookingId(booking.getId());
        for (Payment candidate : existing) {
            if (candidate.getState() == PaymentState.SETTLED) {
                throw new InvalidPaymentStateException(
                        "Booking " + booking.getBookingUid() + " is already paid");
            }
            if (ACTIVE_ATTEMPT_STATES.contains(candidate.getState())) {
                return candidate;
            }
        }
        return newPaymentAttempt(booking, method);
    }

    private Payment newPaymentAttempt(Booking booking, PaymentMethod method) {
        return Payment.builder()
                .bookingId(booking.getId())
                .method(method)
                .providerReference(generateProviderReference(method))
                .amount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .state(PaymentState.INITIATED)
                .build();
    }

    /**
     * Generated once and reused on every retry of this attempt (design doc 8b) — a fresh
     * reference on retry is the standard double-charge bug. Deterministic and
     * collision-resistant: method code, millisecond timestamp, and a random suffix.
     */
    private String generateProviderReference(PaymentMethod method) {
        long timestamp = Instant.now(clock).toEpochMilli();
        int suffix = ThreadLocalRandom.current().nextInt(100000, 999999);
        return method.name() + "-" + timestamp + "-" + suffix;
    }

    private void applyOutcome(Booking booking, Payment payment, GatewayOutcome outcome, String correlationId) {
        switch (outcome) {
            case SETTLED -> {
                payment.transitionTo(PaymentState.SETTLED);
                booking.transitionTo(BookingState.CONFIRMED);
                ledgerService.recordCharge(payment, booking, correlationId);
            }
            case FAILED -> {
                payment.transitionTo(PaymentState.FAILED);
                booking.transitionTo(BookingState.PAYMENT_FAILED);
            }
            case PENDING -> markUnknown(booking, payment);
        }
    }

    private void markUnknown(Booking booking, Payment payment) {
        Instant now = Instant.now(clock);
        Duration firstDelay = statusCheckProperties.delayForAttempt(1).orElse(Duration.ofSeconds(30));
        payment.markUnknown(now, firstDelay);
        booking.transitionTo(BookingState.PAYMENT_UNKNOWN);
    }
}
