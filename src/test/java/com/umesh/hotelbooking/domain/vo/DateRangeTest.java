package com.umesh.hotelbooking.domain.vo;

import com.umesh.hotelbooking.domain.exception.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    @Test
    void nightsForThreeNightStayExcludesCheckoutDay() {
        DateRange range = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

        assertThat(range.nights()).containsExactly(
                LocalDate.of(2026, 9, 10),
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 12));
    }

    @Test
    void nightsForOneNightStayReturnsOneEntry() {
        DateRange range = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));

        assertThat(range.nights()).containsExactly(LocalDate.of(2026, 9, 10));
    }

    @Test
    void checkOutEqualToCheckInThrows() {
        LocalDate day = LocalDate.of(2026, 9, 10);

        assertThatThrownBy(() -> new DateRange(day, day))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void checkOutBeforeCheckInThrows() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 9)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void stayExceedingThirtyNightsThrows() {
        LocalDate checkIn = LocalDate.of(2026, 1, 1);
        LocalDate checkOut = checkIn.plusDays(DateRange.MAX_STAY_NIGHTS + 1);

        assertThatThrownBy(() -> new DateRange(checkIn, checkOut))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void stayOfExactlyMaxNightsIsAllowed() {
        LocalDate checkIn = LocalDate.of(2026, 1, 1);
        LocalDate checkOut = checkIn.plusDays(DateRange.MAX_STAY_NIGHTS);

        DateRange range = new DateRange(checkIn, checkOut);

        assertThat(range.nightCount()).isEqualTo(DateRange.MAX_STAY_NIGHTS);
    }

    @Test
    void overlapsIsFalseWhenOneCheckoutEqualsAnotherCheckin() {
        DateRange guestA = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
        DateRange guestB = new DateRange(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 14));

        assertThat(guestA.overlaps(guestB)).isFalse();
        assertThat(guestB.overlaps(guestA)).isFalse();
    }

    @Test
    void overlapsIsTrueForPartialOverlap() {
        DateRange a = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));
        DateRange b = new DateRange(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 15));

        assertThat(a.overlaps(b)).isTrue();
        assertThat(b.overlaps(a)).isTrue();
    }

    @Test
    void overlapsIsTrueForFullContainment() {
        DateRange outer = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 20));
        DateRange inner = new DateRange(LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 14));

        assertThat(outer.overlaps(inner)).isTrue();
        assertThat(inner.overlaps(outer)).isTrue();
    }

    @Test
    void overlapsIsTrueForIdenticalRanges() {
        DateRange a = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));
        DateRange b = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

        assertThat(a.overlaps(b)).isTrue();
    }

    @Test
    void nightCountAgreesWithNightsSizeAcrossMonthBoundary() {
        DateRange range = new DateRange(LocalDate.of(2026, 1, 28), LocalDate.of(2026, 2, 3));

        assertThat(range.nightCount()).isEqualTo(range.nights().size());
    }

    @Test
    void nightCountAgreesWithNightsSizeAcrossLeapDay() {
        // 2028 is a leap year.
        DateRange range = new DateRange(LocalDate.of(2028, 2, 27), LocalDate.of(2028, 3, 2));

        List<LocalDate> nights = range.nights();
        assertThat(range.nightCount()).isEqualTo(nights.size());
        assertThat(nights).contains(LocalDate.of(2028, 2, 29));
    }

    @Test
    void containsIsTrueOnlyWithinHalfOpenInterval() {
        DateRange range = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

        assertThat(range.contains(LocalDate.of(2026, 9, 10))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 9, 12))).isTrue();
        assertThat(range.contains(LocalDate.of(2026, 9, 13))).isFalse();
        assertThat(range.contains(LocalDate.of(2026, 9, 9))).isFalse();
    }

    @Test
    void isBeforeIsTrueOnceCheckoutHasPassed() {
        DateRange range = new DateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 13));

        assertThat(range.isBefore(LocalDate.of(2026, 9, 12))).isFalse();
        assertThat(range.isBefore(LocalDate.of(2026, 9, 13))).isTrue();
        assertThat(range.isBefore(LocalDate.of(2026, 9, 14))).isTrue();
    }
}
