package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the outbound gateway call itself (design doc 7.3, 7.4, 7.5).
 *
 * @param callTimeout explicit timeout on every gateway call, set below the breaker's
 *     evaluation expectations. Without this, a hanging call never returns, the breaker never
 *     records a failure, and threads accumulate — the breaker is useless without it.
 * @param bulkheadLimit per-provider concurrency cap ({@code @ConcurrencyLimit}). A hanging
 *     UPI provider must not consume all capacity and starve card payments.
 * @param retry bounded retry for the initiate/status calls, gated on idempotency: initiate
 *     is safe to retry only because its reference id is stable across attempts, and status
 *     is naturally idempotent because it is read-only.
 */
@ConfigurationProperties("payment.gateway")
public record PaymentGatewayProperties(Duration callTimeout, int bulkheadLimit, Retry retry) {

    public PaymentGatewayProperties {
        callTimeout = callTimeout == null ? Duration.ofSeconds(2) : callTimeout;
        bulkheadLimit = bulkheadLimit <= 0 ? 5 : bulkheadLimit;
        retry = retry == null ? new Retry(2, Duration.ofMillis(50), Duration.ofMillis(300)) : retry;
    }

    public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {
    }
}
