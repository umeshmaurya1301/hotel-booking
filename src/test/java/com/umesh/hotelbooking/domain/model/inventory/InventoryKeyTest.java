package com.umesh.hotelbooking.domain.model.inventory;

import com.umesh.hotelbooking.domain.vo.DateRange;
import com.umesh.hotelbooking.domain.vo.RoomTypeId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryKeyTest {

    @Test
    void orderedForReturnsKeysInAscendingDateOrder() {
        RoomTypeId roomTypeId = RoomTypeId.of("rt-1");
        DateRange range = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

        List<InventoryKey> keys = InventoryKey.orderedFor(roomTypeId, range);

        assertThat(keys).extracting(InventoryKey::stayDate).containsExactly(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12));
    }

    @Test
    void compareToOrdersByRoomTypeThenDate() {
        InventoryKey a = new InventoryKey(RoomTypeId.of("rt-1"), LocalDate.of(2026, 9, 11));
        InventoryKey b = new InventoryKey(RoomTypeId.of("rt-1"), LocalDate.of(2026, 9, 10));
        InventoryKey c = new InventoryKey(RoomTypeId.of("rt-2"), LocalDate.of(2026, 9, 1));

        assertThat(b).isLessThan(a);
        assertThat(a).isLessThan(c);
    }

    @Test
    void sortingAShuffledListIsDeterministic() {
        RoomTypeId rt1 = RoomTypeId.of("rt-1");
        RoomTypeId rt2 = RoomTypeId.of("rt-2");

        List<InventoryKey> keys = new ArrayList<>(List.of(
                new InventoryKey(rt2, LocalDate.of(2026, 9, 10)),
                new InventoryKey(rt1, LocalDate.of(2026, 9, 12)),
                new InventoryKey(rt1, LocalDate.of(2026, 9, 10)),
                new InventoryKey(rt1, LocalDate.of(2026, 9, 11))
        ));
        Collections.shuffle(keys);

        Collections.sort(keys);

        assertThat(keys).containsExactly(
                new InventoryKey(rt1, LocalDate.of(2026, 9, 10)),
                new InventoryKey(rt1, LocalDate.of(2026, 9, 11)),
                new InventoryKey(rt1, LocalDate.of(2026, 9, 12)),
                new InventoryKey(rt2, LocalDate.of(2026, 9, 10)));
    }
}
