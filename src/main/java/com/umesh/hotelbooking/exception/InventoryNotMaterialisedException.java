package com.umesh.hotelbooking.exception;

import com.umesh.hotelbooking.dto.ErrorCode;

import java.time.LocalDate;

/**
 * Thrown when an admin operation targets a night that has no inventory row yet — typically a
 * date beyond the materialised horizon. Distinct from
 * {@link InventoryUnavailableException}, which means the row exists but is sold out: "no
 * such night" and "that night is full" are different answers and the caller acts on them
 * differently.
 */
public final class InventoryNotMaterialisedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InventoryNotMaterialisedException(String roomTypeUid, LocalDate stayDate) {
        super(ErrorCode.INVENTORY_NOT_MATERIALISED,
                "No inventory row for room type " + roomTypeUid + " on " + stayDate
                        + "; it may be beyond the materialised horizon");
    }
}
