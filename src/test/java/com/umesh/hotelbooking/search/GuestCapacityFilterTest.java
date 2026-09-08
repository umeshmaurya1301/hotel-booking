package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GuestCapacityFilterTest {

    private static RoomType roomType(String uid, int maxGuests) {
        return RoomType.builder()
                .roomTypeUid(uid).name(uid).totalUnits(5).maxGuests(maxGuests)
                .basePricePerNight(new BigDecimal("100.00")).build();
    }

    private static SearchCriteria criteria(int units, int adults, int children) {
        LocalDate day = LocalDate.of(2026, 10, 10);
        return SearchCriteria.of(new SearchPropertiesRequest(
                "Bengaluru", null, day, day.plusDays(1), units, adults, children, null, null, null, null));
    }

    private static Property property() {
        return Property.builder()
                .propertyUid("p1").name("Hotel").city("Bengaluru").cityNormalised("bengaluru")
                .starRating(3).zoneId("Asia/Kolkata").currency("INR").build();
    }

    @Test
    void twoUnitsTimesMaxGuestsTwoAdmitsFourGuests() {
        Property property = property();
        property.addRoomType(roomType("rt1", 2));
        SearchCandidate candidate = SearchCandidate.of(property);

        boolean survived = new GuestCapacityFilter().matches(candidate, criteria(2, 4, 0));

        assertThat(survived).isTrue();
        assertThat(candidate.roomTypes()).hasSize(1);
    }

    @Test
    void twoUnitsTimesMaxGuestsTwoRejectsFiveGuests() {
        Property property = property();
        property.addRoomType(roomType("rt1", 2));
        SearchCandidate candidate = SearchCandidate.of(property);

        boolean survived = new GuestCapacityFilter().matches(candidate, criteria(2, 5, 0));

        assertThat(survived).isFalse();
        assertThat(candidate.roomTypes()).isEmpty();
    }

    @Test
    void aPropertyKeepsOnlyItsSufficientlyLargeRoomTypesRatherThanBeingDroppedWhole() {
        Property property = property();
        property.addRoomType(roomType("small", 2));
        property.addRoomType(roomType("large", 6));
        SearchCandidate candidate = SearchCandidate.of(property);

        boolean survived = new GuestCapacityFilter().matches(candidate, criteria(1, 5, 0));

        assertThat(survived).isTrue();
        assertThat(candidate.roomTypes()).hasSize(1);
        assertThat(candidate.roomTypes().get(0).roomType().getRoomTypeUid()).isEqualTo("large");
    }
}
