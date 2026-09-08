package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyFilterTest {

    private static Property.PropertyBuilder property() {
        return Property.builder()
                .propertyUid("p1")
                .name("Test Hotel")
                .city("Bengaluru")
                .cityNormalised("bengaluru")
                .locality("Indiranagar")
                .starRating(4)
                .zoneId("Asia/Kolkata")
                .currency("INR")
                .amenities(java.util.Set.of(Amenity.WIFI, Amenity.POOL));
    }

    private static SearchCandidate candidateOf(Property property) {
        RoomType roomType = RoomType.builder()
                .roomTypeUid("rt1").name("Room").totalUnits(1).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build();
        property.addRoomType(roomType);
        return SearchCandidate.of(property);
    }

    private static SearchCriteria criteria(String city, String locality, Integer minStarRating, Set<Amenity> amenities) {
        LocalDate day = LocalDate.of(2026, 10, 10);
        return SearchCriteria.of(new SearchPropertiesRequest(
                city, locality, day, day.plusDays(1), 1, 1, 0, null, null, minStarRating, amenities));
    }

    // --- CityFilter ---

    @Test
    void cityFilterMatchesTheNormalisedCity() {
        SearchCandidate candidate = candidateOf(property().build());
        assertThat(new CityFilter().matches(candidate, criteria("Bengaluru", null, null, null))).isTrue();
    }

    @Test
    void cityFilterRejectsADifferentCity() {
        SearchCandidate candidate = candidateOf(property().build());
        assertThat(new CityFilter().matches(candidate, criteria("Mumbai", null, null, null))).isFalse();
    }

    // --- LocalityFilter ---

    @Test
    void localityFilterMatchesEverythingWhenUnspecified() {
        SearchCandidate candidate = candidateOf(property().build());
        assertThat(new LocalityFilter().matches(candidate, criteria("Bengaluru", null, null, null))).isTrue();
    }

    @Test
    void localityFilterIsCaseInsensitive() {
        SearchCandidate candidate = candidateOf(property().build());
        assertThat(new LocalityFilter().matches(candidate, criteria("Bengaluru", "INDIRANAGAR", null, null))).isTrue();
    }

    @Test
    void localityFilterRejectsADifferentLocality() {
        SearchCandidate candidate = candidateOf(property().build());
        assertThat(new LocalityFilter().matches(candidate, criteria("Bengaluru", "Koramangala", null, null))).isFalse();
    }

    @Test
    void localityFilterRejectsWhenPropertyHasNoLocalityButOneWasRequested() {
        SearchCandidate candidate = candidateOf(property().locality(null).build());
        assertThat(new LocalityFilter().matches(candidate, criteria("Bengaluru", "Indiranagar", null, null))).isFalse();
    }

    // --- StarRatingFilter ---

    @Test
    void starRatingFilterMatchesEverythingWhenUnspecified() {
        SearchCandidate candidate = candidateOf(property().starRating(1).build());
        assertThat(new StarRatingFilter().matches(candidate, criteria("Bengaluru", null, null, null))).isTrue();
    }

    @Test
    void starRatingFilterAdmitsAtOrAboveTheFloor() {
        SearchCandidate candidate = candidateOf(property().starRating(4).build());
        assertThat(new StarRatingFilter().matches(candidate, criteria("Bengaluru", null, 4, null))).isTrue();
    }

    @Test
    void starRatingFilterRejectsBelowTheFloor() {
        SearchCandidate candidate = candidateOf(property().starRating(3).build());
        assertThat(new StarRatingFilter().matches(candidate, criteria("Bengaluru", null, 4, null))).isFalse();
    }

    // --- AmenityFilter ---

    @Test
    void amenityFilterMatchesEverythingWhenUnspecified() {
        SearchCandidate candidate = candidateOf(property().amenities(Set.of()).build());
        assertThat(new AmenityFilter().matches(candidate, criteria("Bengaluru", null, null, null))).isTrue();
    }

    @Test
    void amenityFilterRequiresEveryRequestedAmenity() {
        SearchCandidate candidate = candidateOf(property().amenities(Set.of(Amenity.WIFI, Amenity.POOL, Amenity.GYM)).build());
        assertThat(new AmenityFilter().matches(candidate, criteria("Bengaluru", null, null, Set.of(Amenity.WIFI, Amenity.POOL)))).isTrue();
    }

    @Test
    void amenityFilterRejectsWhenOneRequestedAmenityIsMissing() {
        SearchCandidate candidate = candidateOf(property().amenities(Set.of(Amenity.WIFI)).build());
        assertThat(new AmenityFilter().matches(candidate, criteria("Bengaluru", null, null, Set.of(Amenity.WIFI, Amenity.POOL)))).isFalse();
    }
}
