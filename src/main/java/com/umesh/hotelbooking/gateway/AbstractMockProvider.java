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
        String payload = syntheticInstrumentPayload(request);

        return switch (simulate) {
            case SETTLED -> new PaymentResult(GatewayOutcome.SETTLED, request.providerReference(), "approved", payload);
            case FAILED -> new PaymentResult(GatewayOutcome.FAILED, request.providerReference(), "declined by issuer", payload);
            case TIMEOUT -> throw new GatewayTimeoutException(
                    providerCode() + " did not respond in time (simulated)");
            case STUCK_FOREVER, STUCK_THEN_SETTLE, STUCK_THEN_FAIL ->
                    new PaymentResult(GatewayOutcome.PENDING, request.providerReference(), "processing", payload);
        };
    }

    @Override
    @ConcurrencyLimit(5)
    public PaymentResult status(String providerReference) {
        SimulatedOutcome simulate = byReference.getOrDefault(providerReference, SimulatedOutcome.SETTLED);
        int pollNumber = pollCounts.computeIfAbsent(providerReference, key -> new AtomicInteger()).incrementAndGet();
        String payload = syntheticStatusPayload(providerReference);

        GatewayOutcome outcome = switch (simulate) {
            case SETTLED -> GatewayOutcome.SETTLED;
            case FAILED -> GatewayOutcome.FAILED;
            case TIMEOUT -> throw new GatewayTimeoutException(
                    providerCode() + " did not respond in time (simulated)");
            case STUCK_FOREVER -> GatewayOutcome.PENDING;
            case STUCK_THEN_SETTLE -> pollNumber < 2 ? GatewayOutcome.PENDING : GatewayOutcome.SETTLED;
            case STUCK_THEN_FAIL -> pollNumber < 2 ? GatewayOutcome.PENDING : GatewayOutcome.FAILED;
        };
        return new PaymentResult(outcome, providerReference, "status check", payload);
    }

    /**
     * A raw, unredacted, method-shaped provider response (design doc 12.6.6) — {@code
     * request}'s own instrument fields if a caller ever supplies them, otherwise obviously
     * synthetic stand-ins ({@code 4111 1111 1111 1111}, {@code asha@example.com}), since no
     * caller in this codebase collects real instrument data from a guest. {@code cvv} is
     * included deliberately: it is what {@code PayloadRedactor} must drop entirely on the way
     * to any store (design doc 12.6.2), and a redaction test proves nothing against a payload
     * that never carried one.
     */
    private String syntheticInstrumentPayload(PaymentRequest request) {
        String authCode = "AUTH-" + request.providerReference();
        return switch (request.method()) {
            case CARD -> """
                    {"cardNumber":"%s","cvv":"%s","billingName":"%s","authCode":"%s"}""".formatted(
                    orDefault(request.cardNumber(), "4111111111111111"),
                    orDefault(request.cvv(), "123"),
                    orDefault(request.billingName(), "Asha Menon"),
                    authCode);
            case UPI -> """
                    {"vpa":"%s","authCode":"%s"}""".formatted(
                    orDefault(request.vpa(), "asha@upi"), authCode);
            case WALLET -> """
                    {"walletId":"%s","contactPhone":"%s","authCode":"%s"}""".formatted(
                    orDefault(request.walletId(), "WALLET-SYN-0001"),
                    orDefault(request.contactPhone(), "+919876543210"),
                    authCode);
        };
    }

    /**
     * The status-check path has only {@code providerReference} to go on — no {@link
     * PaymentRequest} in hand — so this returns a fixed, obviously-synthetic payload spanning
     * every instrument shape rather than trying to infer the method. This is what actually
     * flows into {@code PaymentStatusCheck.responseSummary} (design doc 12.6.6): PAN, CVV,
     * email and phone shaped fields together, so the leak test has all four to check for.
     */
    private String syntheticStatusPayload(String providerReference) {
        return """
                {"cardNumber":"4111111111111111","cvv":"123","billingName":"Asha Menon",\
                "email":"asha@example.com","phone":"+919876543210","authCode":"AUTH-%s"}""".formatted(providerReference);
    }

    private static String orDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Always settles immediately. Unlike {@code initiate}, refunds have no
     * {@link SimulatedOutcome} lever: a real gateway typically accepts a refund request
     * synchronously even though the underlying money movement takes days, and this system
     * does not build a refund-reconciliation ladder to match payment's (design doc 7.6.6
     * draws that scope line for payment; extending it to refunds would be the same
     * over-engineering one level down). A refund that the gateway rejects outright is a
     * distinct, narrower problem this mock does not need to simulate to demonstrate the
     * cancellation flow.
     */
    @Override
    @ConcurrencyLimit(5)
    public RefundResult refund(RefundRequest request) {
        return new RefundResult(GatewayOutcome.SETTLED, "RFND-" + request.providerReference(), "refund accepted");
    }

    @Override
    @ConcurrencyLimit(5)
    public ReversalResult reverse(ReversalRequest request) {
        return new ReversalResult(GatewayOutcome.SETTLED, "RVRS-" + request.providerReference(), "reversal accepted");
    }

    @Override
    public boolean verifyCallback(byte[] rawBody, String signature, String timestamp) {
        throw new UnsupportedOperationException(
                providerCode() + ": callback verification is built in the webhook phase");
    }
}
