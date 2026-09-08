package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

/**
 * Thrown when the requested unit count cannot accommodate the requested guest count for a
 * room type; raised by the booking-creation path built in a later phase.
 */
public final class GuestCapacityExceededException extends DomainException {

    private static final long serialVersionUID = 1L;

    public GuestCapacityExceededException(String message) {
        super(ErrorCode.GUEST_CAPACITY_EXCEEDED, message);
    }
}
