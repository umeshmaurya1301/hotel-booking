package com.umesh.hotelbooking.domain.exception;

/**
 * Thrown when a {@code DateRange} is constructed with a null bound, a checkout not after
 * checkin, or a span exceeding the maximum stay length.
 */
public final class InvalidDateRangeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidDateRangeException(String message) {
        super("INVALID_DATE_RANGE", message);
    }
}
