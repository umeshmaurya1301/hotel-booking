package com.umesh.hotelbooking.domain.exception;

/**
 * Thrown when a lookup by booking id finds nothing. Takes the raw id string rather than a
 * {@code BookingId} so this package has no dependency on {@code domain.vo}.
 */
public final class BookingNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BookingNotFoundException(String bookingId) {
        super("BOOKING_NOT_FOUND", "No booking found with id " + bookingId);
    }
}
