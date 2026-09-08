package com.umesh.hotelbooking.gateway;

/**
 * The gateway call did not return within {@code payment.gateway.call-timeout}. Recorded as a
 * circuit-breaker failure (design doc 7.1) — never guessed as success or decline, since we
 * genuinely do not know what happened on the other side.
 */
public class GatewayTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
