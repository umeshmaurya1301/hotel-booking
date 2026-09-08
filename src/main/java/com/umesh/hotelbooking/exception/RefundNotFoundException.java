package com.umesh.hotelbooking.exception;

public final class RefundNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RefundNotFoundException(String refundUid) {
        super("REFUND_NOT_FOUND", "No refund found with id " + refundUid);
    }
}
