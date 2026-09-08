package com.umesh.hotelbooking.crypto;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown by {@link SignatureVerifier} for anything that means "reject this callback": a stale
 * timestamp, an unknown provider code, or a signature that does not match (design doc 12.3).
 * Not a {@code DomainException} — it is caught and mapped entirely within {@code
 * controller.webhook}'s own {@code WebhookExceptionHandler}, which is the one place a
 * SYSTEM-category response deliberately returns a non-2xx (design doc 11.4).
 */
public final class SignatureVerificationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final String eventId;

    public SignatureVerificationException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * @param eventId known only once the body has been parsed, so {@link
     *     com.umesh.hotelbooking.crypto.SignatureVerifier} — which acts on raw bytes it never
     *     parses — always passes {@code null} here. {@code InboundWebhookService} re-throws a
     *     copy of this exception carrying the real {@code eventId} once it has one, so the
     *     rejection ack can echo it.
     */
    public SignatureVerificationException(ErrorCode errorCode, String message, String eventId) {
        super(message);
        this.errorCode = errorCode;
        this.eventId = eventId;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String eventId() {
        return eventId;
    }
}
