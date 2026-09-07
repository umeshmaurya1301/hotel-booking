package com.umesh.hotelbooking.domain.model.inventory;

/**
 * Placeholder for the per-night inventory row. Phase 1 needs only enough of this type to give
 * {@code DailyInventoryRepository} a concrete signature; total/booked units and per-night
 * pricing are built in the inventory-materialisation phase.
 */
public final class DailyInventory {

    private final InventoryKey key;

    public DailyInventory(InventoryKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        this.key = key;
    }

    public InventoryKey getKey() {
        return key;
    }
}
