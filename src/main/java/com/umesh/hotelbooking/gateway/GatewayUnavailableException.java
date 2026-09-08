package com.umesh.hotelbooking.gateway;

/**
 * No configured provider can handle the requested method/bank combination, or a provider
 * explicitly signalled it is down. Recorded as a circuit-breaker failure, like
 * {@link GatewayTimeoutException} (design doc 7.1).
 */
public class GatewayUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GatewayUnavailableException(String message) {
        super(message);
    }
}
