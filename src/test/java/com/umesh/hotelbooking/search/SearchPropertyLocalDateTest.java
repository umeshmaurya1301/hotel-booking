package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.config.FieldEncryptionConfig;
import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.service.MutableClock;
import com.umesh.hotelbooking.repository.jpa.JpaStores;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 4.5 trap (task spec §5.4), and the first test in this suite to vary a zone at all —
 * {@code MutableClock} was previously used only by {@code BookingSweeperTest}, for hold
 * expiry, always against a single zone. This fixture is built from scratch rather than copied.
 *
 * <p>{@code Asia/Kolkata} (UTC+5:30) is already one calendar day ahead of {@code
 * Pacific/Honolulu} (UTC-10, no DST) at the chosen instant — a 15.5-hour gap that puts the two
 * zones on different calendar days without needing an artificially extreme offset. A search
 * whose {@code checkIn} lands on the earlier of those two dates must be excluded for the
 * property that has already turned the page to the next day, and admitted for the one that
 * has not.
 */
@DataJpaTest
@Import({FieldEncryptionConfig.class, AvailabilityFilter.class, JpaStores.class})
class SearchPropertyLocalDateTest {

    private static final Instant INSTANT = Instant.parse("2026-09-07T20:00:00Z");
    private static final LocalDate BOUNDARY_DATE = LocalDate.of(2026, 9, 7);

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private DailyInventoryStore dailyInventoryStore;
    @Autowired
    private AvailabilityFilter availabilityFilter;
    @Autowired
    private Clock clock;

    @Test
    void thePropertyAlreadyTomorrowExcludesTheBoundaryDateWhileTheStillTodayPropertyAdmitsIt() {
        // The fixture's premise, asserted directly rather than assumed.
        assertThat(LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))))
                .as("Kolkata has already turned the page")
                .isEqualTo(BOUNDARY_DATE.plusDays(1));
        assertThat(LocalDate.now(clock.withZone(ZoneId.of("Pacific/Honolulu"))))
                .as("Honolulu is still on the boundary date")
                .isEqualTo(BOUNDARY_DATE);

        RoomType kolkataRoomType = RoomType.builder()
                .id(101L).roomTypeUid("rt-kolkata").name("Room").totalUnits(5).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build();
        RoomType honoluluRoomType = RoomType.builder()
                .id(102L).roomTypeUid("rt-honolulu").name("Room").totalUnits(5).maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00")).build();

        // Inventory exists for both, so if Kolkata is excluded it is because of the
        // property-local date check, not incidentally because of missing inventory (§5.3).
        materialise(101L);
        materialise(102L);

        Property kolkataProperty = property("p-kolkata", "Asia/Kolkata");
        kolkataProperty.addRoomType(kolkataRoomType);
        Property honoluluProperty = property("p-honolulu", "Pacific/Honolulu");
        honoluluProperty.addRoomType(honoluluRoomType);

        List<SearchCandidate> candidates = new ArrayList<>(List.of(
                SearchCandidate.of(kolkataProperty), SearchCandidate.of(honoluluProperty)));
        SearchCriteria criteria = SearchCriteria.of(new SearchPropertiesRequest(
                "City", null, BOUNDARY_DATE, BOUNDARY_DATE.plusDays(1), 1, 1, 0, null, null, null, null));

        availabilityFilter.applyBatch(candidates, criteria);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).property().getPropertyUid()).isEqualTo("p-honolulu");
    }

    private void materialise(long roomTypeId) {
        dailyInventoryStore.saveAndFlush(DailyInventory.builder()
                .roomTypeId(roomTypeId).stayDate(BOUNDARY_DATE).totalUnits(5).bookedUnits(0)
                .pricePerUnit(new BigDecimal("8000.00")).currency("INR").build());
    }

    private static Property property(String uid, String zoneId) {
        return Property.builder()
                .propertyUid(uid).name(uid).city("City").cityNormalised("city")
                .starRating(3).zoneId(zoneId).currency("INR").build();
    }
}
