package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the per-night {@code daily_inventory} rows a room type needs (design doc 4.2).
 *
 * <p>Materialisation is eager and bounded: rows for the next {@code inventory.horizon-days}
 * are written up front, so the availability check is a plain indexed read, every night has a
 * row to lock, and the concurrency tests in a later phase have something to contend over.
 * The cost is 10 room types x 90 days = 900 rows per property, plus a job to roll the window
 * forward — accepted knowingly over lazy creation, which would make every read handle
 * "row absent means available" and turn first-booking into its own check-then-act race.
 *
 * <p>Materialisation is idempotent: nights that already have a row are skipped rather than
 * duplicated or overwritten. That matters because rolling the horizon forward re-runs over a
 * range whose earlier part already exists, and because an existing row may carry an admin's
 * manual rate override that a re-run must not silently discard.
 */
@Service
public class InventoryMaterializer {

    private final DailyInventoryStore dailyInventoryStore;

    public InventoryMaterializer(DailyInventoryStore dailyInventoryStore) {
        this.dailyInventoryStore = dailyInventoryStore;
    }

    /**
     * Writes a row for every night in {@code [fromInclusive, toExclusive)} that does not have
     * one yet, pricing each night through {@code strategy}.
     *
     * @return how many rows were actually created
     */
    @Transactional
    public int materialise(RoomType roomType,
                           String currency,
                           LocalDate fromInclusive,
                           LocalDate toExclusive,
                           PricingStrategy strategy) {
        if (!toExclusive.isAfter(fromInclusive)) {
            return 0;
        }

        Set<LocalDate> alreadyMaterialised = existingNights(roomType.getId(), fromInclusive, toExclusive);

        List<DailyInventory> newRows = new ArrayList<>();
        for (LocalDate night = fromInclusive; night.isBefore(toExclusive); night = night.plusDays(1)) {
            if (alreadyMaterialised.contains(night)) {
                continue;
            }
            newRows.add(DailyInventory.builder()
                    .roomTypeId(roomType.getId())
                    .stayDate(night)
                    .totalUnits(roomType.getTotalUnits())
                    .bookedUnits(0)
                    .pricePerUnit(strategy.priceFor(roomType, night))
                    .currency(currency)
                    .build());
        }

        dailyInventoryStore.saveAll(newRows);
        return newRows.size();
    }

    private Set<LocalDate> existingNights(Long roomTypeId, LocalDate fromInclusive, LocalDate toExclusive) {
        List<DailyInventory> existing = dailyInventoryStore
                .findByRoomTypeIdAndStayDateBetween(roomTypeId, fromInclusive, toExclusive.minusDays(1));
        Set<LocalDate> nights = new HashSet<>(existing.size());
        for (DailyInventory row : existing) {
            nights.add(row.getStayDate());
        }
        return nights;
    }
}
