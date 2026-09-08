package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

public final class PaymentNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PaymentNotFoundException(String paymentUid) {
        super(ErrorCode.PAYMENT_NOT_FOUND, "No payment found with id " + paymentUid);
    }
}
