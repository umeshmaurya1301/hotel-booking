package com.umesh.hotelbooking.gateway;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Retrying wrapper around a provider call (design doc 7.4).
 *
 * <p>Retry is gated on idempotency, not applied blindly: {@code initiate} is safe to retry
 * only because {@code PaymentRequest.providerReference()} is generated once by the caller and
 * stays fixed across attempts — without that, a retried initiate would double-charge.
 * {@code status} is naturally safe because it is read-only.
 *
 * <p>Composition order matters (7.4): the breaker must wrap this, not the other way round.
 * Call sites do {@code circuitBreaker.execute(() -> gatewayClient.callInitiate(...))}, so a
 * transient timeout is absorbed by these retries first, and only the outcome of the whole
 * retried attempt counts as one entry in the breaker's window. Retrying against an already
 * OPEN breaker would just be pounding a dependency that has already told us it is unwell.
 *
 * <p>Retry configuration is hardcoded rather than bound to {@code payment.gateway.retry.*}:
 * {@code @Retryable}'s numeric attributes must be compile-time constants, and its
 * {@code *String} variants exist for exactly this, but risking an unverified duration-parsing
 * format on a resilience path is a worse trade than a literal kept manually in sync with the
 * documented config's intent (2 attempts, 50ms initial delay, 300ms cap, x2 backoff).
 */
@Service
public class PaymentGatewayClient {

    @Retryable(
            includes = {GatewayTimeoutException.class},
            maxRetries = 2,
            delay = 50,
            maxDelay = 300,
            multiplier = 2.0,
            jitter = 20,
            timeUnit = TimeUnit.MILLISECONDS)
    public PaymentResult callInitiate(PaymentGatewayProvider provider, PaymentRequest request) {
        return provider.initiate(request);
    }

    @Retryable(
            includes = {GatewayTimeoutException.class},
            maxRetries = 2,
            delay = 50,
            maxDelay = 300,
            multiplier = 2.0,
            jitter = 20,
            timeUnit = TimeUnit.MILLISECONDS)
    public GatewayOutcome callStatus(PaymentGatewayProvider provider, String providerReference) {
        return provider.status(providerReference);
    }
}
