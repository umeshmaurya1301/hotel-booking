package com.umesh.hotelbooking.gateway;

/**
 * Thrown by {@link PaymentCircuitBreaker} instead of attempting a call while OPEN. Handled
 * identically to {@link GatewayTimeoutException} by the caller: the outcome is unobserved,
 * not failed (design doc 7.2).
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
