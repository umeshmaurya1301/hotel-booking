package com.umesh.hotelbooking.gateway;

/**
 * The gateway call did not return in time. Recorded as a circuit-breaker failure (design doc
 * 7.1) — never guessed as success or decline, since we genuinely do not know what happened on
 * the other side.
 *
 * <p>Against the mock providers this is raised directly by {@code SimulatedOutcome.TIMEOUT}
 * rather than by a clock: there is no real socket to time out, and the point of the simulation
 * is the breaker's reaction, not the waiting. A real integration would raise it from the HTTP
 * client's own read timeout.
 */
public class GatewayTimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GatewayTimeoutException(String message) {
        super(message);
    }
}
