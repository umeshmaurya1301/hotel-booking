package com.umesh.hotelbooking.exception;

/**
 * Thrown when a lookup by guest uid finds nothing.
 */
public final class GuestNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public GuestNotFoundException(String guestUid) {
        super("GUEST_NOT_FOUND", "No guest found with id " + guestUid);
    }
}
