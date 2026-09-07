package com.umesh.hotelbooking.exception;

/**
 * Thrown when a lookup by booking id (business uid) finds nothing.
 */
public final class BookingNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BookingNotFoundException(String bookingUid) {
        super("BOOKING_NOT_FOUND", "No booking found with id " + bookingUid);
    }
}
