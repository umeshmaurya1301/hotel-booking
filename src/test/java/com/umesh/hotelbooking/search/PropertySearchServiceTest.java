package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertySearchResult;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.dto.SearchResponse;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end against a fixture this test builds itself, not the {@code demo} profile's seed —
 * {@code @DataJpaTest} slices never see that seed (task spec §10), and building an
 * independent fixture keeps this test from breaking every time the seed's property list
 * changes for reasons unrelated to search. Every property uses a unique, test-local city name
 * for the same reason {@code AbstractBookingConcurrencyTestSupport} uses unique property
 * names: this suite's {@code @SpringBootTest} classes can share a cached application context
 * and its embedded H2 instance, so two tests searching the same city would otherwise see each
 * other's data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "search.max-results=2"
})
class PropertySearchServiceTest {

    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyStore propertyStore;
    @Autowired
    private RoomTypeStore roomTypeStore;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;
    @Autowired
    private PropertySearchService propertySearchService;
    @Autowired
    private Clock clock;

    private static String uniqueCity(String label) {
        return label + System.nanoTime();
    }

    private PropertyResponse onboard(String city, int starRating, Set<Amenity> amenities,
                                     String roomTypeName, int totalUnits, int maxGuests, String basePrice) {
        return onboardingService.onboard(new OnboardPropertyRequest(
                null, "Owner " + System.nanoTime(), null,
                null, null, null, null,
                "Hotel " + System.nanoTime(), city, null, null, null, starRating,
                "Asia/Kolkata", "INR", amenities,
                List.of(new RoomTypeRequest(roomTypeName, totalUnits, maxGuests, new BigDecimal(basePrice))),
                null));
    }

    private void fullyBook(String propertyUid, String roomTypeName, LocalDate night) {
        Property property = propertyStore.findByPropertyUid(propertyUid).orElseThrow();
        RoomType roomType = roomTypeStore.findByPropertyId(property.getId()).stream()
                .filter(rt -> rt.getName().equals(roomTypeName)).findFirst().orElseThrow();
        DailyInventory row = dailyInventoryStore.findByRoomTypeIdAndStayDate(roomType.getId(), night).orElseThrow();
        row.setBookedUnits(row.getTotalUnits());
        dailyInventoryStore.save(row);
    }

    @Test
    void combinedCriteriaReturnExactlyTheExpectedPropertiesWithCorrectRoomTypeDetails() {
        String city = uniqueCity("CombinedCity");
        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(2);
        LocalDate checkOut = checkIn.plusDays(1);

        // Excluded: too few stars.
        onboard(city, 3, Set.of(Amenity.WIFI, Amenity.POOL), "Family", 3, 4, "5000.00");
        // Excluded: room type too small for 4 guests (maxGuests 1).
        onboard(city, 5, Set.of(Amenity.WIFI, Amenity.POOL), "Solo", 5, 1, "3000.00");
        // Matches: 4-star, has WIFI+POOL, room fits 4, price (6000) within [5000, 15000].
        PropertyResponse cheaper = onboard(city, 4, Set.of(Amenity.WIFI, Amenity.POOL), "Family", 3, 4, "6000.00");
        // Matches: 5-star, has WIFI+POOL+SPA (extra amenity is fine), price (12000) within range.
        PropertyResponse pricier = onboard(city, 5, Set.of(Amenity.WIFI, Amenity.POOL, Amenity.SPA), "Family", 2, 4, "12000.00");
        // Excluded: matches every other criterion but is fully booked on the requested night.
        PropertyResponse fullyBooked = onboard(city, 4, Set.of(Amenity.WIFI, Amenity.POOL), "Family", 1, 4, "7000.00");
        fullyBook(fullyBooked.propertyUid(), "Family", checkIn);

        SearchResponse response = propertySearchService.search(new SearchPropertiesRequest(
                city, null, checkIn, checkOut, 1, 4, 0,
                new BigDecimal("5000.00"), new BigDecimal("15000.00"), 4, Set.of(Amenity.WIFI, Amenity.POOL)));

        assertThat(response.truncated()).isFalse();
        assertThat(response.resultCount()).isEqualTo(2);
        assertThat(response.results()).extracting(PropertySearchResult::propertyUid)
                .containsExactly(cheaper.propertyUid(), pricier.propertyUid()); // cheapest first

        PropertySearchResult cheaperResult = response.results().get(0);
        assertThat(cheaperResult.roomTypes()).hasSize(1);
        assertThat(cheaperResult.roomTypes().get(0).stayTotal()).isEqualByComparingTo("6000.00");
        assertThat(cheaperResult.roomTypes().get(0).availableUnits()).isEqualTo(3);
        assertThat(cheaperResult.roomTypes().get(0).currency()).isEqualTo("INR");
    }

    @Test
    void resultsAreCappedAndTruncatedIsSetWhenTheCapBites() {
        String city = uniqueCity("TruncationCity");
        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(2);
        LocalDate checkOut = checkIn.plusDays(1);

        // search.max-results is 2 for this test class; three matching properties must clip to two.
        onboard(city, 3, Set.of(), "Room", 5, 2, "3000.00");
        onboard(city, 3, Set.of(), "Room", 5, 2, "4000.00");
        onboard(city, 3, Set.of(), "Room", 5, 2, "5000.00");

        SearchResponse response = propertySearchService.search(new SearchPropertiesRequest(
                city, null, checkIn, checkOut, 1, 1, 0, null, null, null, null));

        assertThat(response.truncated()).isTrue();
        assertThat(response.resultCount()).isEqualTo(2);
        assertThat(response.results()).hasSize(2);
        // Capped after sorting cheapest-first, not an arbitrary two of the three.
        assertThat(response.results().get(0).roomTypes().get(0).stayTotal()).isEqualByComparingTo("3000.00");
        assertThat(response.results().get(1).roomTypes().get(0).stayTotal()).isEqualByComparingTo("4000.00");
    }

    @Test
    void aCityWithNoPropertiesReturnsAnEmptyNotTruncatedResult() {
        SearchResponse response = propertySearchService.search(new SearchPropertiesRequest(
                uniqueCity("NoSuchCity"), null, LocalDate.now(clock).plusDays(5), LocalDate.now(clock).plusDays(6),
                1, 1, 0, null, null, null, null));

        assertThat(response.resultCount()).isZero();
        assertThat(response.truncated()).isFalse();
        assertThat(response.results()).isEmpty();
    }
}
