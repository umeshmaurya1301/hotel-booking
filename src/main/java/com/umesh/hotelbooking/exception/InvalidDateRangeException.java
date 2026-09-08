package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown when a booking's checkout is not after its checkin, or the stay exceeds the maximum
 * allowed length.
 */
public final class InvalidDateRangeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(String message) {
        super(ErrorCode.INVALID_DATE_RANGE, message);
    }
}
