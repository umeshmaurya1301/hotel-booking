package com.umesh.hotelbooking.exception;

/**
 * Thrown when a msgId is replayed with a different request body. Surfaces the client bug
 * rather than silently returning the stored response for a different request than was sent.
 */
public final class IdempotencyPayloadMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    public IdempotencyPayloadMismatchException(String msgId) {
        super("MSG_ID_PAYLOAD_MISMATCH",
                "msgId " + msgId + " was already used with a different request body");
    }
}
