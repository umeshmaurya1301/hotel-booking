package com.umesh.hotelbooking.exception;

import java.time.LocalDate;

/**
 * Thrown when the in-JVM inventory lock could not be acquired within its timeout.
 *
 * <p>A distinct outcome from {@link InventoryUnavailableException}: the room may well be
 * free, we simply could not get far enough to find out. Callers should treat it as retryable
 * contention, not as a sold-out night.
 */
public final class InventoryLockTimeoutException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InventoryLockTimeoutException(Long roomTypeId, LocalDate stayDate) {
        super("INVENTORY_LOCK_TIMEOUT",
                "Timed out waiting for the inventory lock on room type " + roomTypeId + " for " + stayDate);
    }
}
