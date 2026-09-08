package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.config.CircuitBreakerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A hand-written circuit breaker wrapping the payment gateway call — the only unreliable
 * remote dependency in this system (design doc 7.1).
 *
 * <p>Hand-written rather than Resilience4j because {@code resilience4j-spring-boot4} does not
 * resolve against Spring Boot 4.1: it is absent from Maven Central entirely, confirmed in the
 * Phase 0 de-risking step. The shape mirrors Resilience4j's own model deliberately — COUNT_BASED
 * sliding window, a failure-rate threshold, a timed OPEN state, a bounded HALF_OPEN trial —
 * so swapping in the real library later, if it ships, is a drop-in change against the same
 * {@link CircuitBreakerProperties} and the same call sites.
 *
 * <p>One shared instance across every provider: this guards "the payment gateway" as a class
 * of dependency, matching the design document's singular {@code paymentGateway} instance
 * name. Per-provider isolation is the bulkhead's job (7.5, {@code @ConcurrencyLimit} on each
 * mock provider), not this breaker's.
 *
 * <p>Only {@link GatewayTimeoutException} and {@link GatewayUnavailableException} count as
 * failures here — a declined payment is a normal {@link PaymentResult} with outcome
 * {@code FAILED}, not a thrown exception, so it never reaches this class at all and cannot
 * accidentally open the breaker during ordinary operation. That distinction is the one this
 * design calls out as a frequent mistake.
 *
 * <p>Deliberately {@code synchronized} rather than lock-free: the state machine's correctness
 * is what this class exists to demonstrate, and the call volume in this exercise never
 * approaches a point where that costs anything real. A production rewrite would shard this
 * per provider and use atomics; that is not this problem.
 */
@Component
public class PaymentCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(PaymentCircuitBreaker.class);

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final CircuitBreakerProperties properties;
    private final Clock clock;

    private State state = State.CLOSED;
    private final Deque<Boolean> window = new ArrayDeque<>();
    private Instant openedAt = Instant.EPOCH;
    private int halfOpenAttempts;

    public PaymentCircuitBreaker(CircuitBreakerProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Runs {@code call}, or throws {@link CircuitBreakerOpenException} without attempting it
     * if the breaker is OPEN. Any other exception the call throws propagates unchanged and
     * untouched by this class — it says nothing about the gateway's health.
     */
    public synchronized <T> T execute(Supplier<T> call) {
        admitOrThrow();
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (GatewayTimeoutException | GatewayUnavailableException e) {
            onFailure();
            throw e;
        }
    }

    private void admitOrThrow() {
        if (state == State.OPEN) {
            if (Instant.now(clock).isBefore(openedAt.plus(properties.waitDurationInOpenState()))) {
                throw new CircuitBreakerOpenException(
                        "Payment gateway circuit breaker is OPEN until "
                                + openedAt.plus(properties.waitDurationInOpenState()));
            }
            state = State.HALF_OPEN;
            halfOpenAttempts = 0;
            log.info("Payment circuit breaker HALF_OPEN: admitting up to {} trial call(s)",
                    properties.permittedCallsInHalfOpenState());
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenAttempts >= properties.permittedCallsInHalfOpenState()) {
                throw new CircuitBreakerOpenException("Payment gateway circuit breaker is HALF_OPEN and at capacity");
            }
            halfOpenAttempts++;
        }
    }

    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            if (halfOpenAttempts >= properties.permittedCallsInHalfOpenState()) {
                state = State.CLOSED;
                window.clear();
                log.info("Payment circuit breaker CLOSED after successful trial calls");
            }
        } else {
            record(true);
        }
    }

    private void onFailure() {
        if (state == State.HALF_OPEN) {
            open();
        } else {
            record(false);
            if (window.size() >= properties.slidingWindowSize() && failureRatePercent() >= properties.failureRateThreshold()) {
                open();
            }
        }
    }

    private void record(boolean success) {
        window.addLast(success);
        while (window.size() > properties.slidingWindowSize()) {
            window.removeFirst();
        }
    }

    private int failureRatePercent() {
        long failures = window.stream().filter(success -> !success).count();
        return (int) Math.round(100.0 * failures / window.size());
    }

    private void open() {
        state = State.OPEN;
        openedAt = Instant.now(clock);
        window.clear();
        log.warn("Payment circuit breaker OPEN until {}", openedAt.plus(properties.waitDurationInOpenState()));
    }

    /** Visible for an admin/health view of gateway breaker state. */
    public synchronized String currentState() {
        return state.name();
    }
}
