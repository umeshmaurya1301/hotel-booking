package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * design doc 5.2's rule at the boundary, and the §5.3 missing-inventory case — the two things
 * only a real batched query against real rows can prove.
 */
@DataJpaTest
@Import(AvailabilityFilter.class)
class AvailabilityFilterTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 10, 10);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            // Well before DAY1 in every zone, so the §5.4 property-local-today check never
            // fires in this test - that behaviour has its own dedicated test.
            return Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private DailyInventoryRepository dailyInventoryRepository;
    @Autowired
    private AvailabilityFilter availabilityFilter;

    private static RoomType roomTypeWithId(long id) {
        return RoomType.builder()
                .id(id).roomTypeUid("rt-" + id).name("Room " + id).totalUnits(10).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build();
    }

    private static Property propertyWithRoomType(RoomType roomType) {
        Property property = Property.builder()
                .propertyUid("p1").name("Hotel").city("Bengaluru").cityNormalised("bengaluru")
                .starRating(3).zoneId("Asia/Kolkata").currency("INR").build();
        property.addRoomType(roomType);
        return property;
    }

    private void materialise(long roomTypeId, LocalDate stayDate, int totalUnits, int bookedUnits) {
        dailyInventoryRepository.saveAndFlush(DailyInventory.builder()
                .roomTypeId(roomTypeId).stayDate(stayDate).totalUnits(totalUnits).bookedUnits(bookedUnits)
                .pricePerUnit(new BigDecimal("8000.00")).currency("INR").build());
    }

    private static SearchCriteria criteria(LocalDate checkIn, LocalDate checkOut, int units) {
        return SearchCriteria.of(new SearchPropertiesRequest(
                "Bengaluru", null, checkIn, checkOut, units, 1, 0, null, null, null, null));
    }

    @Test
    void aRoomTypeWithExactlyUnitsFreeMatches() {
        materialise(1L, DAY1, 5, 3); // 2 free
        SearchCandidate candidate = SearchCandidate.of(propertyWithRoomType(roomTypeWithId(1L)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(candidate));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(1), 2));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).roomTypes()).hasSize(1);
        assertThat(candidates.get(0).roomTypes().get(0).availableUnits()).isEqualTo(2);
    }

    @Test
    void aRoomTypeOneUnitShortDoesNotMatch() {
        materialise(2L, DAY1, 5, 4); // 1 free
        SearchCandidate candidate = SearchCandidate.of(propertyWithRoomType(roomTypeWithId(2L)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(candidate));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(1), 2));

        // AvailabilityFilter prunes the room-type list; dropping a now-empty candidate from
        // the top-level list is SearchFilterChain's job (see SearchFilterChainTest), not
        // this filter's - this test calls applyBatch directly, without the chain.
        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).roomTypes()).isEmpty();
    }

    @Test
    void oneInsufficientNightInAThreeNightRangeExcludesTheRoomType() {
        materialise(3L, DAY1, 5, 0);
        materialise(3L, DAY1.plusDays(1), 5, 5); // full on the middle night
        materialise(3L, DAY1.plusDays(2), 5, 0);
        SearchCandidate candidate = SearchCandidate.of(propertyWithRoomType(roomTypeWithId(3L)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(candidate));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(3), 1));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).roomTypes()).isEmpty();
    }

    @Test
    void allThreeNightsAvailableMatchesAndComputesStayTotalAndMinAvailability() {
        materialise(4L, DAY1, 5, 0);
        materialise(4L, DAY1.plusDays(1), 5, 3); // 2 free - the binding minimum
        materialise(4L, DAY1.plusDays(2), 5, 1);
        SearchCandidate candidate = SearchCandidate.of(propertyWithRoomType(roomTypeWithId(4L)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(candidate));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(3), 1));

        assertThat(candidates).hasSize(1);
        RoomTypeCandidate survivor = candidates.get(0).roomTypes().get(0);
        assertThat(survivor.availableUnits()).isEqualTo(2);
        assertThat(survivor.stayTotal()).isEqualByComparingTo("24000.00"); // 8000 x 1 unit x 3 nights
    }

    /** §5.3: a range extending past the materialised horizon excludes the room type rather
     * than throwing InventoryNotMaterialisedException. */
    @Test
    void aRangeExtendingPastTheMaterialisedHorizonExcludesRatherThanThrows() {
        materialise(5L, DAY1, 5, 0); // only the first of two requested nights exists
        SearchCandidate candidate = SearchCandidate.of(propertyWithRoomType(roomTypeWithId(5L)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(candidate));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(2), 1));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).roomTypes()).isEmpty();
    }

    @Test
    void aPropertyWithMultipleRoomTypesKeepsOnlyTheAvailableOnes() {
        materialise(6L, DAY1, 5, 5); // full
        materialise(7L, DAY1, 5, 0); // free
        Property property = Property.builder()
                .propertyUid("p2").name("Hotel Two").city("Bengaluru").cityNormalised("bengaluru")
                .starRating(3).zoneId("Asia/Kolkata").currency("INR").build();
        property.addRoomType(roomTypeWithId(6L));
        property.addRoomType(roomTypeWithId(7L));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(SearchCandidate.of(property)));

        availabilityFilter.applyBatch(candidates, criteria(DAY1, DAY1.plusDays(1), 1));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).roomTypes()).hasSize(1);
        assertThat(candidates.get(0).roomTypes().get(0).roomType().getId()).isEqualTo(7L);
    }
}
