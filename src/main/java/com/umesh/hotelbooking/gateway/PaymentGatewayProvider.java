package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.entity.PaymentMethod;

/**
 * A payment gateway, in whatever shape a specific bank's integration takes (design doc 6.1).
 *
 * <p>This is a provider-registry / SPI-style plugin, not classical Java SPI: there is no
 * {@code ServiceLoader} and no {@code META-INF/services}. Spring's component scan is what
 * discovers implementations, which is idiomatic in a Boot application and gives lifecycle
 * management for free. {@code ServiceLoader} would be the right tool if providers shipped as
 * external JARs dropped onto the classpath — they do not here.
 *
 * <p>Adding a bank is one new {@code @Component} implementing this interface. Nothing else —
 * not the router, not {@code PaymentService}, not any controller — changes. That is the
 * extensibility claim, and it is verifiable by inspection.
 *
 * <p>{@code refund}, {@code reverse} and {@code verifyCallback} are declared now so the
 * contract is settled, but are only exercised starting in the phases that actually perform
 * refunds/reversals and receive webhooks; the mock implementations here throw
 * {@link UnsupportedOperationException} for them in the meantime rather than pretending to
 * support something untested.
 */
public interface PaymentGatewayProvider {

    boolean supports(PaymentMethod method, String bankCode);

    /** Idempotent by {@code request.providerReference()} — calling this twice with the same
     * reference must not charge twice. */
    PaymentResult initiate(PaymentRequest request);

    GatewayOutcome status(String providerReference);

    RefundResult refund(RefundRequest request);

    ReversalResult reverse(ReversalRequest request);

    boolean verifyCallback(byte[] rawBody, String signature, String timestamp);

    String providerCode();
}
