package com.umesh.hotelbooking.gateway;

/** What a provider's {@code initiate} or {@code status} call answered with. */
public enum GatewayOutcome {
    SETTLED,
    FAILED,
    PENDING
}
