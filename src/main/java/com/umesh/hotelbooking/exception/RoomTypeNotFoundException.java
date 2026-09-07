package com.umesh.hotelbooking.exception;

/**
 * Thrown when a lookup by business uid finds nothing.
 */
public final class RoomTypeNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RoomTypeNotFoundException(String roomTypeUid) {
        super("ROOM_TYPE_NOT_FOUND", "No room type found with id " + roomTypeUid);
    }
}
