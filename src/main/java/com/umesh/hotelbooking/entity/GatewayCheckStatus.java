package com.umesh.hotelbooking.entity;

/**
 * What a status poll (or an initiate call) came back with, plus the one outcome that is
 * <em>ours</em>: {@code ERROR} means our call to the gateway failed (timeout, breaker open),
 * which is not evidence about the transaction and must not consume the ladder's attempt
 * budget — only a genuine {@code PENDING} answer does (design doc 7.6.4).
 */
public enum GatewayCheckStatus {
    SETTLED,
    FAILED,
    PENDING,
    ERROR
}
