package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.exception.OwnerNotFoundException;
import com.umesh.hotelbooking.exception.PropertyGroupNotFoundException;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.repository.PropertyGroupStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Onboarding behaviour, including the two things most worth pinning: that a single property
 * is structurally a group of one, and that the opening horizon is measured in the property's
 * timezone rather than the server's.
 *
 * <p>The horizon is shrunk to 5 nights here so assertions are about counts a reader can
 * check by hand rather than about 90 rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=5",
        "inventory.default-pricing-strategy=FLAT"
})
@Transactional
class PropertyOnboardingServiceTest {

    /**
     * Fixed at 2026-09-07T18:40:00Z, which is 2026-09-08T00:10 in Asia/Kolkata — already the
     * next calendar day there while UTC is still on the 7th. Every property-local assertion
     * below depends on that gap being real.
     */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-07T18:40:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyStore propertyStore;
    @Autowired
    private PropertyGroupStore propertyGroupStore;
    @Autowired
    private RoomTypeStore roomTypeStore;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;

    private OnboardPropertyRequest independentRequest(String name, String city) {
        return new OnboardPropertyRequest(
                null, name + " Owner", "owner@example.test",
                null, null, null,
                null,
                name, city, "Central", null, null, 4, "Asia/Kolkata", "INR",
                Set.of(Amenity.WIFI, Amenity.PARKING),
                List.of(new RoomTypeRequest("Deluxe King", 10, 2, new BigDecimal("8000.00"))),
                null);
    }

    @Test
    void anIndependentPropertyGetsAGroupContainingExactlyItself() {
        PropertyResponse response = onboardingService.onboard(independentRequest("Lakeview Inn", "Bengaluru"));

        assertThat(response.propertyGroupUid()).isNotBlank();

        Property saved = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        List<Property> siblings = propertyStore.findByPropertyGroupId(saved.getPropertyGroup().getId());

        assertThat(siblings).hasSize(1);
        assertThat(siblings.get(0).getPropertyUid()).isEqualTo(response.propertyUid());
    }

    @Test
    void aChainAttachesItsPropertiesToOneSharedGroup() {
        PropertyResponse first = onboardingService.onboard(independentRequest("Meridian Bengaluru", "Bengaluru"));

        OnboardPropertyRequest second = new OnboardPropertyRequest(
                first.ownerUid(), null, null,
                first.propertyGroupUid(), null, null,
                null,
                "Meridian Mumbai", "Mumbai", "BKC", null, null, 5, "Asia/Kolkata", "INR",
                Set.of(Amenity.WIFI),
                List.of(new RoomTypeRequest("Deluxe King", 20, 2, new BigDecimal("11000.00"))),
                null);
        PropertyResponse secondResponse = onboardingService.onboard(second);

        assertThat(secondResponse.propertyGroupUid()).isEqualTo(first.propertyGroupUid());
        assertThat(secondResponse.ownerUid()).isEqualTo(first.ownerUid());

        Property saved = propertyStore.findByPropertyUid(secondResponse.propertyUid()).orElseThrow();
        assertThat(propertyStore.findByPropertyGroupId(saved.getPropertyGroup().getId())).hasSize(2);
    }

    @Test
    void bothOwnershipShapesTakeTheSameCodePath() {
        // The point of the group-of-one: after onboarding, nothing distinguishes an
        // independent hotel from a chain member except how many siblings its group has.
        PropertyResponse independent = onboardingService.onboard(independentRequest("Solo Stay", "Goa"));
        PropertyResponse chainFirst = onboardingService.onboard(independentRequest("Chain One", "Jaipur"));

        assertThat(independent.propertyGroupUid()).isNotBlank();
        assertThat(chainFirst.propertyGroupUid()).isNotBlank();
        assertThat(propertyGroupStore.findByPropertyGroupUid(independent.propertyGroupUid()))
                .isPresent();
    }

    @Test
    void onboardingMaterialisesOneRowPerNightPerRoomType() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, "Two Room Types Owner", null, null, null, null,
                null,
                "Twin Types Hotel", "Chennai", null, null, null, 3, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Standard", 5, 2, new BigDecimal("3000.00")),
                        new RoomTypeRequest("Suite", 2, 4, new BigDecimal("9000.00"))),
                null);

        PropertyResponse response = onboardingService.onboard(request);

        Property saved = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        assertThat(roomTypeStore.findByPropertyId(saved.getId())).hasSize(2);
        for (var roomType : roomTypeStore.findByPropertyId(saved.getId())) {
            assertThat(dailyInventoryStore.countByRoomTypeId(roomType.getId())).isEqualTo(5);
        }
    }

    @Test
    void theHorizonStartsAtThePropertysLocalToday_notTheServers() {
        // The classic UTC/IST bug: at 18:40Z it is already the 8th in Kolkata. Starting from
        // the server's date would leave the 8th unmaterialised and therefore unbookable.
        PropertyResponse response = onboardingService.onboard(independentRequest("Midnight Hotel", "Bengaluru"));

        Property saved = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        Long roomTypeId = roomTypeStore.findByPropertyId(saved.getId()).get(0).getId();
        List<DailyInventory> rows = dailyInventoryStore.findByRoomTypeIdAndStayDateBetween(
                roomTypeId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(rows).extracting(DailyInventory::getStayDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 9, 8),
                        LocalDate.of(2026, 9, 9),
                        LocalDate.of(2026, 9, 10),
                        LocalDate.of(2026, 9, 11),
                        LocalDate.of(2026, 9, 12));
        assertThat(rows).extracting(DailyInventory::getStayDate)
                .doesNotContain(LocalDate.of(2026, 9, 7));
    }

    @Test
    void materialisedRowsCarryThePriceTheStrategyProduced() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, "Surge Owner", null, null, null, null,
                null,
                "Surge Hotel", "Mumbai", null, null, null, 4, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 10, 2, new BigDecimal("8000.00"))),
                "WEEKEND_SURGE");

        PropertyResponse response = onboardingService.onboard(request);

        Property saved = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        Long roomTypeId = roomTypeStore.findByPropertyId(saved.getId()).get(0).getId();

        // 2026-09-11 is a Friday, 2026-09-10 a Thursday.
        BigDecimal friday = dailyInventoryStore
                .findByRoomTypeIdAndStayDate(roomTypeId, LocalDate.of(2026, 9, 11))
                .orElseThrow().getPricePerUnit();
        BigDecimal thursday = dailyInventoryStore
                .findByRoomTypeIdAndStayDate(roomTypeId, LocalDate.of(2026, 9, 10))
                .orElseThrow().getPricePerUnit();

        assertThat(thursday).isEqualByComparingTo("8000.00");
        assertThat(friday).isEqualByComparingTo("10000.00");
    }

    @Test
    void cityIsStoredNormalisedForSearchAndOriginalForDisplay() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, "Casing Owner", null, null, null, null,
                null,
                "Casing Hotel", "  BENGALURU  ", null, null, null, 3, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Std", 2, 2, new BigDecimal("1000.00"))),
                null);

        PropertyResponse response = onboardingService.onboard(request);

        Property saved = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        assertThat(saved.getCity()).isEqualTo("  BENGALURU  ");
        assertThat(saved.getCityNormalised()).isEqualTo("bengaluru");
        assertThat(propertyStore.findByCityNormalised("bengaluru"))
                .extracting(Property::getPropertyUid)
                .contains(response.propertyUid());
    }

    @Test
    void anUnknownOwnerUidIsRejected() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                "no-such-owner", null, null, null, null, null,
                null,
                "Orphan Hotel", "Pune", null, null, null, 3, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Std", 2, 2, new BigDecimal("1000.00"))),
                null);

        assertThatThrownBy(() -> onboardingService.onboard(request))
                .isInstanceOf(OwnerNotFoundException.class);
    }

    @Test
    void anUnknownGroupUidIsRejected() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, "Some Owner", null, "no-such-group", null, null,
                null,
                "Orphan Hotel", "Pune", null, null, null, 3, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Std", 2, 2, new BigDecimal("1000.00"))),
                null);

        assertThatThrownBy(() -> onboardingService.onboard(request))
                .isInstanceOf(PropertyGroupNotFoundException.class);
    }

    @Test
    void neitherOwnerUidNorOwnerNameIsRejected() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, null, null, null, null, null,
                null,
                "Ownerless Hotel", "Pune", null, null, null, 3, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Std", 2, 2, new BigDecimal("1000.00"))),
                null);

        assertThatThrownBy(() -> onboardingService.onboard(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ownerUid or ownerName");
    }

    @Test
    void anUnparseableZoneIsRejectedBeforeAnythingIsWritten() {
        OnboardPropertyRequest request = new OnboardPropertyRequest(
                null, "Zone Owner", null, null, null, null,
                null,
                "Nowhere Inn", "Pune", null, null, null, 3, "Mars/Olympus", "INR", null,
                List.of(new RoomTypeRequest("Std", 2, 2, new BigDecimal("1000.00"))),
                null);

        assertThatThrownBy(() -> onboardingService.onboard(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Mars/Olympus");
    }
}
