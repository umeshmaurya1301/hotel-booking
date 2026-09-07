package com.umesh.hotelbooking.domain.model.inventory;

import com.umesh.hotelbooking.domain.vo.DateRange;
import com.umesh.hotelbooking.domain.vo.RoomTypeId;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Identity of one room-type/night: the lock key and unique-constraint identity of a single
 * {@code daily_inventory} row.
 *
 * <p>{@link Comparable} here is a correctness requirement, not a convenience. A multi-night
 * reservation holds one database row lock per night for the duration of its transaction; two
 * bookings acquiring the same nights in opposite order would deadlock. Always locking nights
 * in ascending order — enforced by sorting on this key — makes that circular wait
 * structurally impossible. Do not remove {@code Comparable} without also removing the
 * deadlock-prevention guarantee it provides.
 */
public record InventoryKey(RoomTypeId roomTypeId, LocalDate stayDate) implements Comparable<InventoryKey> {

    private static final Comparator<InventoryKey> ORDER =
            Comparator.comparing((InventoryKey key) -> key.roomTypeId().value())
                    .thenComparing(InventoryKey::stayDate);

    public InventoryKey {
        if (roomTypeId == null) {
            throw new IllegalArgumentException("roomTypeId must not be null");
        }
        if (stayDate == null) {
            throw new IllegalArgumentException("stayDate must not be null");
        }
    }

    @Override
    public int compareTo(InventoryKey other) {
        return ORDER.compare(this, other);
    }

    /**
     * Keys for every night in {@code range}, sorted ascending, so callers cannot accidentally
     * acquire locks in an unsorted, deadlock-prone order.
     */
    public static List<InventoryKey> orderedFor(RoomTypeId id, DateRange range) {
        return range.nights().stream()
                .map(date -> new InventoryKey(id, date))
                .sorted()
                .toList();
    }
}
