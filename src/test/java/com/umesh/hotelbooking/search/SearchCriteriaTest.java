package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.exception.InvalidDateRangeException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCriteriaTest {

    private static SearchPropertiesRequest request(String city, LocalDate checkIn, LocalDate checkOut,
                                                    Integer children, Integer minStarRating, Set<Amenity> amenities) {
        return new SearchPropertiesRequest(city, null, checkIn, checkOut, 1, 2, children,
                null, null, minStarRating, amenities);
    }

    @Test
    void aOneNightRangeExpandsToExactlyOneNight() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(request("Bengaluru", day, day.plusDays(1), 0, null, null));

        assertThat(criteria.nights()).containsExactly(day);
    }

    @Test
    void aThreeNightRangeExpandsCheckoutExclusive() {
        LocalDate checkIn = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(request("Bengaluru", checkIn, checkIn.plusDays(3), 0, null, null));

        assertThat(criteria.nights()).containsExactly(checkIn, checkIn.plusDays(1), checkIn.plusDays(2));
    }

    @Test
    void checkOutEqualToCheckInThrows() {
        LocalDate day = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> SearchCriteria.of(request("Bengaluru", day, day, 0, null, null)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void checkOutBeforeCheckInThrows() {
        LocalDate day = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> SearchCriteria.of(request("Bengaluru", day, day.minusDays(1), 0, null, null)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void nullChildrenNormalisesToZeroAndFeedsTotalGuests() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(request("Bengaluru", day, day.plusDays(1), null, null, null));

        // 2 adults (fixed in the request() helper) + 0 children.
        assertThat(criteria.totalGuests()).isEqualTo(2);
    }

    @Test
    void nullAmenitiesNormalisesToEmptySetNotNull() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(request("Bengaluru", day, day.plusDays(1), 0, null, null));

        assertThat(criteria.amenities()).isNotNull().isEmpty();
    }

    @Test
    void nullMinStarRatingNormalisesToZero() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(request("Bengaluru", day, day.plusDays(1), 0, null, null));

        assertThat(criteria.minStarRating()).isZero();
    }

    @Test
    void aSuppliedMinStarRatingAndAmenitiesPassThrough() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(
                request("Bengaluru", day, day.plusDays(1), 1, 4, Set.of(Amenity.WIFI, Amenity.POOL)));

        assertThat(criteria.minStarRating()).isEqualTo(4);
        assertThat(criteria.amenities()).containsExactlyInAnyOrder(Amenity.WIFI, Amenity.POOL);
        assertThat(criteria.totalGuests()).isEqualTo(3);
    }

    @Test
    void cityNormalisationMatchesPropertysOwnNormaliser() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchCriteria criteria = SearchCriteria.of(
                request(" Bengaluru  City ", day, day.plusDays(1), 0, null, null));

        assertThat(criteria.cityNormalised()).isEqualTo(com.umesh.hotelbooking.entity.Property.normalise(" Bengaluru  City "));
        assertThat(criteria.cityNormalised()).isEqualTo("bengaluru city");
    }

    @Test
    void minPriceAndMaxPricePassThroughUnchanged() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        SearchPropertiesRequest request = new SearchPropertiesRequest(
                "Bengaluru", null, day, day.plusDays(1), 1, 1, 0,
                new BigDecimal("1000.00"), new BigDecimal("20000.00"), null, null);

        SearchCriteria criteria = SearchCriteria.of(request);

        assertThat(criteria.minPricePerStay()).isEqualByComparingTo("1000.00");
        assertThat(criteria.maxPricePerStay()).isEqualByComparingTo("20000.00");
    }
}
