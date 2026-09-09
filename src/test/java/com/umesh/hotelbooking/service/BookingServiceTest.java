package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.RateOverrideRequest;
import com.umesh.hotelbooking.dto.RepriceRequest;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.exception.GuestCapacityExceededException;
import com.umesh.hotelbooking.exception.GuestNotFoundException;
import com.umesh.hotelbooking.exception.InvalidDateRangeException;
import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.exception.RoomTypeNotFoundException;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false"
})
class BookingServiceTest extends AbstractBookingConcurrencyTestSupport {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private InventoryAdminService inventoryAdminService;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;

    @Test
    void aBookingHoldsOneLineItemPerNightAndTotalsThem() {
        Fixture fixture = onboardRoomType("Line Item Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(3), 1, 2, 0, null));

        assertThat(booking.nights()).isEqualTo(3);
        assertThat(booking.lineItems()).hasSize(3);
        assertThat(booking.lineItems()).extracting(item -> item.stayDate())
                .containsExactly(checkIn, checkIn.plusDays(1), checkIn.plusDays(2));
        assertThat(booking.totalAmount()).isEqualByComparingTo("24000.00");
        assertThat(booking.state()).isEqualTo(BookingState.CREATED);
        assertThat(booking.holdExpiresAt()).isAfter(booking.createdAt());
    }

    @Test
    void checkoutDayIsNotChargedBecauseItIsNotANightSlept() {
        Fixture fixture = onboardRoomType("Checkout Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null));

        assertThat(booking.lineItems()).hasSize(1);
        assertThat(booking.lineItems().get(0).stayDate()).isEqualTo(checkIn);
        assertThat(booking.totalAmount()).isEqualByComparingTo("8000.00");
    }

    @Test
    void multiUnitBookingsChargeEveryUnitOnEveryNight() {
        Fixture fixture = onboardRoomType("Multi Unit Total Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(3), 2, 4, 0, null));

        assertThat(booking.lineItems()).allSatisfy(item -> {
            assertThat(item.units()).isEqualTo(2);
            assertThat(item.lineTotal()).isEqualByComparingTo("16000.00");
        });
        assertThat(booking.totalAmount()).isEqualByComparingTo("48000.00");
        assertThat(dailyInventoryStore
                .findByRoomTypeIdAndStayDate(fixture.roomTypeId(), checkIn).orElseThrow()
                .getBookedUnits()).isEqualTo(2);
    }

    @Test
    void nightsPricedDifferentlyProduceATotalThatIsTheirSum() {
        Fixture fixture = onboardRoomType("Per Night Price Hotel", 5);
        LocalDate checkIn = fixture.firstNight();
        inventoryAdminService.overrideNight(fixture.roomTypeUid(),
                new RateOverrideRequest(checkIn.plusDays(1), new BigDecimal("12000.00"), null));

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(3), 1, 1, 0, null));

        assertThat(booking.lineItems()).extracting(item -> item.pricePerUnit())
                .containsExactly(
                        new BigDecimal("8000.00"), new BigDecimal("12000.00"), new BigDecimal("8000.00"));
        assertThat(booking.totalAmount()).isEqualByComparingTo("28000.00");
    }

    /**
     * The snapshot guarantee. A rate change after the fact must never alter what an existing
     * booking owes — without per-night line items the only options would be recomputing
     * (wrong) or storing a bare total (unauditable).
     */
    @Test
    void repricingAfterBookingDoesNotChangeWhatTheBookingOwes() {
        Fixture fixture = onboardRoomType("Snapshot Hotel", 5);
        LocalDate checkIn = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(2), 1, 1, 0, null));
        BigDecimal originalTotal = booking.totalAmount();

        inventoryAdminService.reprice(new RepriceRequest(
                fixture.roomTypeUid(), checkIn, checkIn.plusDays(5), "WEEKEND_SURGE"));
        inventoryAdminService.overrideNight(fixture.roomTypeUid(),
                new RateOverrideRequest(checkIn, new BigDecimal("99000.00"), null));

        BookingResponse reloaded = bookingService.find(booking.bookingUid());

        assertThat(reloaded.totalAmount()).isEqualByComparingTo(originalTotal);
        assertThat(reloaded.lineItems()).isEqualTo(booking.lineItems());
    }

    @Test
    void capacityIsCheckedAgainstUnitsNotAgainstOneRoom() {
        // maxGuests is 4 per room in the fixture: 2 rooms sleep 8, so 9 is too many.
        Fixture fixture = onboardRoomType("Capacity Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 2, 9, 0, null)))
                .isInstanceOf(GuestCapacityExceededException.class);

        // The same guest count across enough rooms is fine.
        assertThat(bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 3, 9, 0, null)))
                .isNotNull();
    }

    @Test
    void capacityCountsChildrenToo() {
        Fixture fixture = onboardRoomType("Children Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 1, 3, 3, null)))
                .isInstanceOf(GuestCapacityExceededException.class);
    }

    @Test
    void aNightBeyondTheMaterialisedHorizonIsNotBookable() {
        Fixture fixture = onboardRoomType("Horizon Hotel", 5);
        LocalDate beyond = fixture.firstNight().plusYears(1);

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), beyond, beyond.plusDays(1), 1, 1, 0, null)))
                .isInstanceOf(InventoryUnavailableException.class);
    }

    @Test
    void aZeroNightStayIsRejectedBeforeAnythingIsReserved() {
        Fixture fixture = onboardRoomType("Zero Night Hotel", 5);
        LocalDate day = fixture.firstNight();

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), day, day, 1, 1, 0, null)))
                .isInstanceOf(InvalidDateRangeException.class);

        assertThat(dailyInventoryStore
                .findByRoomTypeIdAndStayDate(fixture.roomTypeId(), day).orElseThrow()
                .getBookedUnits())
                .as("a rejected request must not have reserved anything")
                .isZero();
    }

    @Test
    void anUnknownRoomTypeIsRejected() {
        LocalDate today = LocalDate.of(2026, 9, 10);

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, "no-such-room-type", today, today.plusDays(1), 1, 1, 0, null)))
                .isInstanceOf(RoomTypeNotFoundException.class);
    }

    @Test
    void anUnknownGuestUidIsRejectedRatherThanSilentlyCreatingOne() {
        Fixture fixture = onboardRoomType("Unknown Guest Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                "no-such-guest", fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null)))
                .isInstanceOf(GuestNotFoundException.class);
    }

    @Test
    void aFirstTimeBookerGetsAGuestCreatedForThem() {
        Fixture fixture = onboardRoomType("New Guest Hotel", 5);
        LocalDate checkIn = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null));

        assertThat(booking.guestUid()).isNotBlank();

        // That guest can then be reused by uid on a later booking.
        BookingResponse second = bookingService.create(freshMeta(), new CreateBookingRequest(
                booking.guestUid(), fixture.roomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null));
        assertThat(second.guestUid()).isEqualTo(booking.guestUid());
    }

    @Test
    void aBookingCanBeFetchedByItsBusinessUid() {
        Fixture fixture = onboardRoomType("Fetch Hotel", 5);
        LocalDate checkIn = fixture.firstNight();
        BookingResponse created = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), checkIn, checkIn.plusDays(2), 1, 1, 0, null));

        BookingResponse fetched = bookingService.find(created.bookingUid());

        assertThat(fetched.bookingUid()).isEqualTo(created.bookingUid());
        assertThat(fetched.propertyUid()).isEqualTo(fixture.propertyUid());
        assertThat(fetched.roomTypeUid()).isEqualTo(fixture.roomTypeUid());
        assertThat(fetched.lineItems()).hasSize(2);
    }
}
