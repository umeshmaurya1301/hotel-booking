package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for the hand-written circuit breaker (design doc 7.1).
 *
 * <p>Shaped to mirror Resilience4j's own configuration keys deliberately: this breaker exists
 * because {@code resilience4j-spring-boot4} does not resolve against Spring Boot 4.1 (no such
 * artifact exists on Maven Central as of this build — see the Phase 0 de-risking note), not
 * because the design changed. Swapping in the real library later, if it ships, should be a
 * drop-in change of implementation with these same settings.
 *
 * @param slidingWindowSize how many recent calls the failure rate is computed over
 * @param failureRateThreshold percent of failures in the window that opens the breaker
 * @param waitDurationInOpenState how long the breaker stays OPEN before allowing a trial call
 * @param permittedCallsInHalfOpenState trial calls allowed through in HALF_OPEN before
 *     deciding to close (all succeed) or re-open (any fails)
 */
@ConfigurationProperties("payment.circuit-breaker")
public record CircuitBreakerProperties(
        int slidingWindowSize,
        int failureRateThreshold,
        Duration waitDurationInOpenState,
        int permittedCallsInHalfOpenState) {

    public CircuitBreakerProperties {
        slidingWindowSize = slidingWindowSize <= 0 ? 10 : slidingWindowSize;
        failureRateThreshold = failureRateThreshold <= 0 ? 50 : failureRateThreshold;
        waitDurationInOpenState = waitDurationInOpenState == null ? Duration.ofSeconds(5) : waitDurationInOpenState;
        permittedCallsInHalfOpenState = permittedCallsInHalfOpenState <= 0 ? 3 : permittedCallsInHalfOpenState;
    }
}
