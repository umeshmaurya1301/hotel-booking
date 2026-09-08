package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

import java.time.LocalDate;

/**
 * Thrown when a reservation cannot be satisfied for a given room type and night. Names the
 * specific night that failed so the caller can re-search intelligently rather than retrying
 * blindly; raised by the reservation path built in a later phase.
 */
public final class InventoryUnavailableException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InventoryUnavailableException(String roomTypeUid, LocalDate stayDate) {
        super(ErrorCode.INVENTORY_UNAVAILABLE, "No availability for room type " + roomTypeUid + " on " + stayDate);
    }
}
