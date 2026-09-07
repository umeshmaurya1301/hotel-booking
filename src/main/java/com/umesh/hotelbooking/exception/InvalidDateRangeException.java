package com.umesh.hotelbooking.exception;

/**
 * Thrown when a booking's checkout is not after its checkin, or the stay exceeds the maximum
 * allowed length.
 */
public final class InvalidDateRangeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(String message) {
        super("INVALID_DATE_RANGE", message);
    }
}
