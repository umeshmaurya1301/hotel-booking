package com.umesh.hotelbooking.exception;

/**
 * Thrown for a payment operation that is not a legal move from the payment's or booking's
 * current state — paying an already-settled booking, or resolving a payment that is not
 * awaiting manual review.
 */
public final class InvalidPaymentStateException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidPaymentStateException(String message) {
        super("INVALID_PAYMENT_STATE", message);
    }
}
