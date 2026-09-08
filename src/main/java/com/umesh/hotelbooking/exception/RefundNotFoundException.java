package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

public final class RefundNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RefundNotFoundException(String refundUid) {
        super(ErrorCode.REFUND_NOT_FOUND, "No refund found with id " + refundUid);
    }
}
