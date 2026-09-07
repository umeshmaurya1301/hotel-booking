package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.inventory.DailyInventory;
import com.umesh.hotelbooking.domain.model.inventory.InventoryKey;

import java.util.Optional;

/**
 * Persistence port for {@link DailyInventory}. Minimal for Phase 1; the reservation phase
 * extends this with the atomic conditional-increment operation that is the actual
 * concurrency mechanism (see the design doc §5.2) — deliberately not exposed here.
 */
public interface DailyInventoryRepository {

    DailyInventory save(DailyInventory dailyInventory);

    Optional<DailyInventory> findByKey(InventoryKey key);
}
