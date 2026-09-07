package com.umesh.hotelbooking.domain.vo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnitCountAndGuestCountTest {

    @Test
    void unitCountBelowOneThrows() {
        assertThatThrownBy(() -> new UnitCount(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitCountAboveMaxThrows() {
        assertThatThrownBy(() -> new UnitCount(UnitCount.MAX_UNITS_PER_BOOKING + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unitCountAtMaxIsAllowed() {
        assertThat(new UnitCount(UnitCount.MAX_UNITS_PER_BOOKING).value())
                .isEqualTo(UnitCount.MAX_UNITS_PER_BOOKING);
    }

    @Test
    void guestCountWithZeroAdultsThrows() {
        assertThatThrownBy(() -> new GuestCount(0, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void guestCountWithNegativeChildrenThrows() {
        assertThatThrownBy(() -> new GuestCount(2, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void totalSumsAdultsAndChildren() {
        assertThat(new GuestCount(2, 3).total()).isEqualTo(5);
    }
}
