package com.umesh.hotelbooking.dto;

import org.springframework.http.HttpStatus;

/**
 * Stable, greppable error identifier for every API failure, replacing the free-text
 * {@code String} codes the {@code DomainException} hierarchy carried before this phase.
 *
 * <p>Each constant owns its default HTTP status, which is what lets {@code
 * GlobalExceptionHandler} derive a response status from the exception alone rather than from
 * three separate {@code Set<String>} blocks that had to be kept in sync with the exception
 * hierarchy by hand. Adding an exception with an existing code now needs no change here or in
 * the handler.
 *
 * <p>Lives in {@code dto} rather than {@code exception} deliberately: this is the wire
 * contract, and {@code exception} depends on it, not the other way round.
 */
public enum ErrorCode {

    // 404 — lookup by business id found nothing
    PROPERTY_NOT_FOUND(HttpStatus.NOT_FOUND),
    PROPERTY_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND),
    ROOM_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
    GUEST_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVENTORY_NOT_MATERIALISED(HttpStatus.NOT_FOUND),

    // 409 — conflicts with current state
    INVENTORY_UNAVAILABLE(HttpStatus.CONFLICT),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT),
    REFUND_EXCEEDS_CHARGE(HttpStatus.CONFLICT),

    // 422 — design doc 8a: same msgId, different body is a client bug, distinct from a plain 400
    MSG_ID_PAYLOAD_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),

    // 400 — malformed or business-invalid request
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    GUEST_CAPACITY_EXCEEDED(HttpStatus.BAD_REQUEST),
    INVALID_PAYMENT_STATE(HttpStatus.BAD_REQUEST),
    UNKNOWN_PRICING_STRATEGY(HttpStatus.BAD_REQUEST),
    UNKNOWN_REFUND_POLICY(HttpStatus.BAD_REQUEST),

    // 403 — stubbed role separation (design doc 11.4)
    FORBIDDEN(HttpStatus.FORBIDDEN),

    // 503 / 504 — resilience outcomes surfaced from the gateway package
    INVENTORY_LOCK_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE),
    PAYMENT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
    PAYMENT_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    CIRCUIT_OPEN(HttpStatus.SERVICE_UNAVAILABLE),

    // 401 — webhook signature verification (design doc 12.3); the one SYSTEM-category case
    // where a non-2xx is deliberate — see WebhookExceptionHandler
    SIGNATURE_INVALID(HttpStatus.UNAUTHORIZED),
    REPLAY_WINDOW_EXCEEDED(HttpStatus.UNAUTHORIZED),

    // 500 — catch-all
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
