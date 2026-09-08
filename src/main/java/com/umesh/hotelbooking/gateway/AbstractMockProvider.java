package com.umesh.hotelbooking.gateway;

import org.springframework.resilience.annotation.ConcurrencyLimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared simulation machinery for the mock providers. Every real behaviour a
 * {@link PaymentGatewayProvider} needs to demonstrate — settlement, decline, timeout, and a
 * transaction that resolves only after being polled — lives here; each concrete provider
 * supplies only which {@code PaymentMethod} it handles and its own code.
 *
 * <p>{@code @ConcurrencyLimit} is the bulkhead of design doc 7.5, applied per concrete
 * provider bean (each subclass is its own Spring bean, so a hanging UPI provider cannot
 * consume the card provider's capacity) — annotated here on the inherited method, which
 * Spring's method-level advice picks up the same way it would on an override.
 */
public abstract class AbstractMockProvider implements PaymentGatewayProvider {

    /** What each in-flight simulated transaction should ultimately answer with. */
    private final ConcurrentHashMap<String, SimulatedOutcome> byReference = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> pollCounts = new ConcurrentHashMap<>();

    @Override
    @ConcurrencyLimit(5)
    public PaymentResult initiate(PaymentRequest request) {
        SimulatedOutcome simulate = request.simulate() == null ? SimulatedOutcome.SETTLED : request.simulate();
        byReference.put(request.providerReference(), simulate);

        return switch (simulate) {
            case SETTLED -> new PaymentResult(GatewayOutcome.SETTLED, request.providerReference(), "approved");
            case FAILED -> new PaymentResult(GatewayOutcome.FAILED, request.providerReference(), "declined by issuer");
            case TIMEOUT -> throw new GatewayTimeoutException(
                    providerCode() + " did not respond in time (simulated)");
            case STUCK_FOREVER, STUCK_THEN_SETTLE, STUCK_THEN_FAIL ->
                    new PaymentResult(GatewayOutcome.PENDING, request.providerReference(), "processing");
        };
    }

    @Override
    @ConcurrencyLimit(5)
    public GatewayOutcome status(String providerReference) {
        SimulatedOutcome simulate = byReference.getOrDefault(providerReference, SimulatedOutcome.SETTLED);
        int pollNumber = pollCounts.computeIfAbsent(providerReference, key -> new AtomicInteger()).incrementAndGet();

        return switch (simulate) {
            case SETTLED -> GatewayOutcome.SETTLED;
            case FAILED -> GatewayOutcome.FAILED;
            case TIMEOUT -> throw new GatewayTimeoutException(
                    providerCode() + " did not respond in time (simulated)");
            case STUCK_FOREVER -> GatewayOutcome.PENDING;
            case STUCK_THEN_SETTLE -> pollNumber < 2 ? GatewayOutcome.PENDING : GatewayOutcome.SETTLED;
            case STUCK_THEN_FAIL -> pollNumber < 2 ? GatewayOutcome.PENDING : GatewayOutcome.FAILED;
        };
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        throw new UnsupportedOperationException(providerCode() + ": refund is built in the cancellation phase");
    }

    @Override
    public ReversalResult reverse(ReversalRequest request) {
        throw new UnsupportedOperationException(providerCode() + ": reversal is built in the cancellation phase");
    }

    @Override
    public boolean verifyCallback(byte[] rawBody, String signature, String timestamp) {
        throw new UnsupportedOperationException(
                providerCode() + ": callback verification is built in the webhook phase");
    }
}
