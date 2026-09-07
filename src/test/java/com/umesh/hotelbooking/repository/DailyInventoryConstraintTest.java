package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.DailyInventory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the database-level guards on {@code daily_inventory} are real, not just documented.
 *
 * <p>These constraints are layer 3 of the concurrency defence (design doc 5.2.4): the
 * reservation path in the next phase relies on an atomic conditional UPDATE for correctness,
 * and this check constraint is what makes overbooking impossible even if that logic is
 * bypassed by a hand-written query or a future code path that forgets the rule. Application
 * logic can be wrong; a check constraint cannot be argued with.
 */
@DataJpaTest
class DailyInventoryConstraintTest {

    @Autowired
    private DailyInventoryRepository dailyInventoryRepository;

    private DailyInventory.DailyInventoryBuilder validRow() {
        return DailyInventory.builder()
                .roomTypeId(1L)
                .stayDate(LocalDate.of(2026, 9, 10))
                .totalUnits(10)
                .bookedUnits(0)
                .pricePerUnit(new BigDecimal("8000.00"))
                .currency("INR");
    }

    @Test
    void aValidRowPersists() {
        DailyInventory saved = dailyInventoryRepository.saveAndFlush(validRow().build());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.availableUnits()).isEqualTo(10);
    }

    @Test
    void bookedUnitsExceedingTotalUnitsIsRejectedByTheDatabase() {
        DailyInventory overbooked = validRow().totalUnits(10).bookedUnits(11).build();

        assertThatThrownBy(() -> dailyInventoryRepository.saveAndFlush(overbooked))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bookedUnitsEqualToTotalUnitsIsAllowed() {
        // Sold out is legal; over-sold is not. The boundary matters.
        DailyInventory soldOut = validRow().totalUnits(10).bookedUnits(10).build();

        DailyInventory saved = dailyInventoryRepository.saveAndFlush(soldOut);

        assertThat(saved.availableUnits()).isZero();
        assertThat(saved.hasCapacityFor(1)).isFalse();
    }

    @Test
    void negativeBookedUnitsIsRejected() {
        DailyInventory negative = validRow().bookedUnits(-1).build();

        assertThatThrownBy(() -> dailyInventoryRepository.saveAndFlush(negative))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nonPositivePriceIsRejected() {
        DailyInventory free = validRow().pricePerUnit(BigDecimal.ZERO).build();

        assertThatThrownBy(() -> dailyInventoryRepository.saveAndFlush(free))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoRowsForTheSameRoomTypeAndNightAreRejected() {
        // One counter per room-night. A duplicate would let two bookings each see capacity.
        dailyInventoryRepository.saveAndFlush(validRow().build());

        assertThatThrownBy(() -> dailyInventoryRepository.saveAndFlush(validRow().build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameNightForADifferentRoomTypeIsFine() {
        dailyInventoryRepository.saveAndFlush(validRow().roomTypeId(1L).build());
        DailyInventory other = dailyInventoryRepository.saveAndFlush(validRow().roomTypeId(2L).build());

        assertThat(other.getId()).isNotNull();
    }
}
