package com.umesh.hotelbooking.exception;

public final class PaymentNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(String paymentUid) {
        super("PAYMENT_NOT_FOUND", "No payment found with id " + paymentUid);
    }
}
