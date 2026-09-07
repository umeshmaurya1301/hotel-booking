package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.ExtendHorizonRequest;
import com.umesh.hotelbooking.dto.InventoryResponse;
import com.umesh.hotelbooking.dto.MaterialisationResponse;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RateOverrideRequest;
import com.umesh.hotelbooking.dto.RepriceRequest;
import com.umesh.hotelbooking.dto.RepriceResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.exception.InventoryNotMaterialisedException;
import com.umesh.hotelbooking.exception.RoomTypeNotFoundException;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=5",
        "inventory.default-pricing-strategy=FLAT"
})
@Transactional
class InventoryAdminServiceTest {

    /** 2026-09-07T18:40Z is already 2026-09-08 in Asia/Kolkata, so night 1 is the 8th. */
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-07T18:40:00Z");
    private static final LocalDate FIRST_NIGHT = LocalDate.of(2026, 9, 8);

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
    private InventoryAdminService inventoryAdminService;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private DailyInventoryRepository dailyInventoryRepository;

    private String propertyUid;
    private String roomTypeUid;
    private Long roomTypeId;

    @BeforeEach
    void onboardOneProperty() {
        PropertyResponse response = onboardingService.onboard(new OnboardPropertyRequest(
                null, "Admin Test Owner", null, null, null, null,
                "Admin Test Hotel", "Bengaluru", null, null, null, 4, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 10, 2, new BigDecimal("8000.00"))),
                null));
        propertyUid = response.propertyUid();
        roomTypeUid = response.roomTypes().get(0).roomTypeUid();

        Property property = propertyRepository.findByPropertyUid(propertyUid).orElseThrow();
        roomTypeId = roomTypeRepository.findByPropertyId(property.getId()).get(0).getId();
    }

    @Test
    void extendingTheHorizonCreatesOnlyTheMissingNights() {
        assertThat(dailyInventoryRepository.countByRoomTypeId(roomTypeId)).isEqualTo(5);

        MaterialisationResponse response = inventoryAdminService.extendHorizon(
                new ExtendHorizonRequest(propertyUid, 8, null));

        assertThat(response.nightsCreated()).isEqualTo(3);
        assertThat(response.roomTypesTouched()).isEqualTo(1);
        assertThat(dailyInventoryRepository.countByRoomTypeId(roomTypeId)).isEqualTo(8);
    }

    @Test
    void extendingTwiceIsIdempotent() {
        inventoryAdminService.extendHorizon(new ExtendHorizonRequest(propertyUid, 8, null));

        MaterialisationResponse second = inventoryAdminService.extendHorizon(
                new ExtendHorizonRequest(propertyUid, 8, null));

        assertThat(second.nightsCreated()).isZero();
        assertThat(dailyInventoryRepository.countByRoomTypeId(roomTypeId)).isEqualTo(8);
    }

    @Test
    void extendingDoesNotOverwriteAnExistingManualOverride() {
        // An operator's hand-set rate must survive the nightly horizon roll.
        inventoryAdminService.overrideNight(roomTypeUid,
                new RateOverrideRequest(FIRST_NIGHT, new BigDecimal("25000.00"), null));

        inventoryAdminService.extendHorizon(new ExtendHorizonRequest(propertyUid, 10, null));

        DailyInventory overridden = dailyInventoryRepository
                .findByRoomTypeIdAndStayDate(roomTypeId, FIRST_NIGHT).orElseThrow();
        assertThat(overridden.getPricePerUnit()).isEqualByComparingTo("25000.00");
    }

    @Test
    void repriceAppliesTheNamedStrategyAcrossTheRange() {
        // 2026-09-11 Friday and 2026-09-12 Saturday surge; the 10th (Thursday) does not.
        RepriceResponse response = inventoryAdminService.reprice(new RepriceRequest(
                roomTypeUid, FIRST_NIGHT, LocalDate.of(2026, 9, 12), "WEEKEND_SURGE"));

        assertThat(response.nightsRepriced()).isEqualTo(5);
        assertThat(priceOn(LocalDate.of(2026, 9, 10))).isEqualByComparingTo("8000.00");
        assertThat(priceOn(LocalDate.of(2026, 9, 11))).isEqualByComparingTo("10000.00");
        assertThat(priceOn(LocalDate.of(2026, 9, 12))).isEqualByComparingTo("10000.00");
    }

    @Test
    void repriceDoesNotCreateNightsBeyondTheHorizon() {
        // Repricing changes prices; extending creates nights. Conflating them would let a
        // typo'd date range silently materialise a year of inventory.
        long before = dailyInventoryRepository.countByRoomTypeId(roomTypeId);

        RepriceResponse response = inventoryAdminService.reprice(new RepriceRequest(
                roomTypeUid, FIRST_NIGHT, FIRST_NIGHT.plusYears(1), "WEEKEND_SURGE"));

        assertThat(response.nightsRepriced()).isEqualTo((int) before);
        assertThat(dailyInventoryRepository.countByRoomTypeId(roomTypeId)).isEqualTo(before);
    }

    @Test
    void repriceRejectsAnInvertedRange() {
        assertThatThrownBy(() -> inventoryAdminService.reprice(new RepriceRequest(
                roomTypeUid, LocalDate.of(2026, 9, 12), LocalDate.of(2026, 9, 8), "FLAT")))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void repriceRejectsAnUnknownRoomType() {
        assertThatThrownBy(() -> inventoryAdminService.reprice(new RepriceRequest(
                "no-such-room-type", FIRST_NIGHT, FIRST_NIGHT, "FLAT")))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }

    @Test
    void overrideChangesOneNightOnly() {
        inventoryAdminService.overrideNight(roomTypeUid,
                new RateOverrideRequest(FIRST_NIGHT, new BigDecimal("25000.00"), 5));

        assertThat(priceOn(FIRST_NIGHT)).isEqualByComparingTo("25000.00");
        assertThat(priceOn(FIRST_NIGHT.plusDays(1))).isEqualByComparingTo("8000.00");

        DailyInventory row = dailyInventoryRepository
                .findByRoomTypeIdAndStayDate(roomTypeId, FIRST_NIGHT).orElseThrow();
        assertThat(row.getTotalUnits()).isEqualTo(5);
    }

    @Test
    void overrideRejectsAnEmptyRequest() {
        assertThatThrownBy(() -> inventoryAdminService.overrideNight(roomTypeUid,
                new RateOverrideRequest(FIRST_NIGHT, null, null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void overrideRejectsReducingUnitsBelowWhatIsAlreadyBooked() {
        // Turn a clean constraint violation into an answer the operator can act on.
        DailyInventory row = dailyInventoryRepository
                .findByRoomTypeIdAndStayDate(roomTypeId, FIRST_NIGHT).orElseThrow();
        row.setBookedUnits(6);
        dailyInventoryRepository.saveAndFlush(row);

        assertThatThrownBy(() -> inventoryAdminService.overrideNight(roomTypeUid,
                new RateOverrideRequest(FIRST_NIGHT, null, 4)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("already booked");
    }

    @Test
    void overrideRejectsANightThatWasNeverMaterialised() {
        assertThatThrownBy(() -> inventoryAdminService.overrideNight(roomTypeUid,
                new RateOverrideRequest(LocalDate.of(2030, 1, 1), new BigDecimal("100.00"), null)))
                .isInstanceOf(InventoryNotMaterialisedException.class);
    }

    @Test
    void viewReturnsTheRangeInclusive() {
        List<InventoryResponse> rows = inventoryAdminService.view(
                roomTypeUid, FIRST_NIGHT, FIRST_NIGHT.plusDays(2));

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).availableUnits()).isEqualTo(10);
    }

    private BigDecimal priceOn(LocalDate date) {
        return dailyInventoryRepository.findByRoomTypeIdAndStayDate(roomTypeId, date)
                .orElseThrow().getPricePerUnit();
    }
}
