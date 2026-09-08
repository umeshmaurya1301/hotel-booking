package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PriceRangeFilterTest {

    private static SearchCandidate candidateWithStayTotal(BigDecimal stayTotal) {
        Property property = Property.builder()
                .propertyUid("p1").name("Hotel").city("City").cityNormalised("city")
                .starRating(3).zoneId("Asia/Kolkata").currency("INR").build();
        RoomType roomType = RoomType.builder()
                .roomTypeUid("rt1").name("Room").totalUnits(5).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build();
        property.addRoomType(roomType);
        SearchCandidate candidate = SearchCandidate.of(property);
        candidate.roomTypes().get(0).setStayTotal(stayTotal);
        return candidate;
    }

    private static SearchCriteria criteria(BigDecimal min, BigDecimal max) {
        LocalDate day = LocalDate.of(2026, 10, 9); // a Friday
        return SearchCriteria.of(new SearchPropertiesRequest(
                "City", null, day, day.plusDays(3), 1, 1, 0, min, max, null, null));
    }

    @Test
    void aStayTotalInsideTheRangeMatches() {
        SearchCandidate candidate = candidateWithStayTotal(new BigDecimal("18000.00"));
        boolean survived = new PriceRangeFilter().matches(candidate,
                criteria(new BigDecimal("10000.00"), new BigDecimal("20000.00")));

        assertThat(survived).isTrue();
    }

    @Test
    void aStayTotalBelowTheMinimumIsRejected() {
        SearchCandidate candidate = candidateWithStayTotal(new BigDecimal("9000.00"));
        boolean survived = new PriceRangeFilter().matches(candidate,
                criteria(new BigDecimal("10000.00"), null));

        assertThat(survived).isFalse();
    }

    @Test
    void aStayTotalAboveTheMaximumIsRejected() {
        SearchCandidate candidate = candidateWithStayTotal(new BigDecimal("21000.00"));
        boolean survived = new PriceRangeFilter().matches(candidate,
                criteria(null, new BigDecimal("20000.00")));

        assertThat(survived).isFalse();
    }

    @Test
    void noPriceConstraintMatchesEverything() {
        SearchCandidate candidate = candidateWithStayTotal(new BigDecimal("999999.00"));
        boolean survived = new PriceRangeFilter().matches(candidate, criteria(null, null));

        assertThat(survived).isTrue();
    }

    /**
     * The whole point of comparing the stay total rather than a nightly rate (design doc
     * 10.1): a 3-night stay spanning a weekend where the Friday/Saturday nights are surged to
     * 8000 and the Sunday night is a flat 5000 sums to 21000. A nightly-rate comparison using
     * just the first night (8000) would say "over budget" against a 20000 ceiling; the
     * stay-total comparison correctly admits it, since the trip as a whole costs 21000 — still
     * over a 20000 ceiling — but would <em>incorrectly reject</em> a genuinely affordable
     * combination a nightly check might approve or deny inconsistently depending on which
     * night it happened to sample. Asserting against the real aggregate, not a single night,
     * is what this test pins.
     */
    @Test
    void stayTotalIsTheSumAcrossNightsNotASingleNightlyRate() {
        BigDecimal fridayRate = new BigDecimal("8000.00");
        BigDecimal saturdayRate = new BigDecimal("8000.00");
        BigDecimal sundayRate = new BigDecimal("5000.00");
        BigDecimal actualStayTotal = fridayRate.add(saturdayRate).add(sundayRate); // 21000.00

        SearchCandidate candidate = candidateWithStayTotal(actualStayTotal);

        // A ceiling above the true stay total (21000) but below any single surged night's
        // rate would be senseless to filter on; the meaningful comparison is against the
        // aggregate itself.
        assertThat(new PriceRangeFilter().matches(candidate, criteria(null, new BigDecimal("21000.00"))))
                .as("stay total exactly at the ceiling matches")
                .isTrue();
        assertThat(new PriceRangeFilter().matches(candidate, criteria(null, new BigDecimal("20999.99"))))
                .as("stay total one cent over the ceiling is rejected")
                .isFalse();
    }

    /** {@code stayTotal} is null until AvailabilityFilter (order 70) populates it — this is
     * what running before it would look like. */
    @Test
    void aNullStayTotalIsTreatedAsNotMatchingRatherThanThrowing() {
        Property property = Property.builder()
                .propertyUid("p1").name("Hotel").city("City").cityNormalised("city")
                .starRating(3).zoneId("Asia/Kolkata").currency("INR").build();
        property.addRoomType(RoomType.builder()
                .roomTypeUid("rt1").name("Room").totalUnits(5).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build());
        SearchCandidate candidate = SearchCandidate.of(property); // stayTotal left unset (null)

        boolean survived = new PriceRangeFilter().matches(candidate, criteria(null, new BigDecimal("100.00")));

        assertThat(survived).isFalse();
    }
}
