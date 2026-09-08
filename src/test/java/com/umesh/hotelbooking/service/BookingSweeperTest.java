package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.SweepResponse;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sweeper is what stops inventory leaking. Every abandoned booking would otherwise take
 * its room-nights off sale permanently.
 *
 * <p>Time is controlled through a {@link MutableClock} rather than slept through, so hold
 * expiry is tested at the real boundary instead of at whatever a shortened TTL happens to do
 * on a fast machine.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.hold-ttl=15m",
        "booking.sweeper.enabled=false"
})
class BookingSweeperTest extends AbstractBookingConcurrencyTestSupport {

    /** 18:40Z is already the next calendar day in Asia/Kolkata — the zone gap matters below. */
    private static final Instant START = Instant.parse("2026-09-07T18:40:00Z");

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingSweeper bookingSweeper;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private DailyInventoryRepository dailyInventoryRepository;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    /**
     * These tests cannot be {@code @Transactional} — the sweeper runs its own transactions,
     * and a test-managed one would swallow them — so isolation has to be explicit. Bookings
     * from an earlier test would otherwise be picked up by a later sweep and counted in its
     * totals. Each test onboards its own property, so only bookings need clearing.
     */
    @BeforeEach
    void clearBookings() {
        bookingRepository.deleteAll();
    }

    @Test
    void aLapsedHoldIsExpiredAndItsRoomNightsGoBackOnSale() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Expiry Hotel", 1);
        LocalDate night = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));
        assertThat(bookedUnits(fixture, night)).isEqualTo(1);

        clock().advance(Duration.ofMinutes(16));
        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.holdsExpired()).isEqualTo(1);
        assertThat(state(booking)).isEqualTo(BookingState.EXPIRED);
        assertThat(bookedUnits(fixture, night))
                .as("the released night must be back on sale").isZero();

        // And genuinely bookable again by someone else — the point of releasing it.
        assertThat(bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null))).isNotNull();
    }

    @Test
    void aHoldThatHasNotLapsedIsLeftAlone() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Live Hold Hotel", 1);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));

        clock().advance(Duration.ofMinutes(14));
        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.holdsExpired()).isZero();
        assertThat(state(booking)).isEqualTo(BookingState.CREATED);
        assertThat(bookedUnits(fixture, night)).isEqualTo(1);
    }

    @Test
    void everyNightOfAMultiNightHoldIsReleased() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Multi Night Expiry Hotel", 2);
        LocalDate night = fixture.firstNight();
        bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(3), 2, 2, 0, null));

        clock().advance(Duration.ofHours(1));
        bookingSweeper.sweep();

        assertThat(bookedUnits(fixture, night)).isZero();
        assertThat(bookedUnits(fixture, night.plusDays(1))).isZero();
        assertThat(bookedUnits(fixture, night.plusDays(2))).isZero();
    }

    /**
     * The sweeper-versus-payment race, made deterministic: the payment got there first and
     * the booking is already CONFIRMED, so the sweep must be a no-op and must not release
     * rooms that are now paid for.
     */
    @Test
    void aBookingConfirmedBeforeTheSweepIsNeitherExpiredNorReleased() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Race Hotel", 1);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));

        confirm(booking);
        clock().advance(Duration.ofHours(1));

        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.holdsExpired()).isZero();
        assertThat(state(booking)).isEqualTo(BookingState.CONFIRMED);
        assertThat(bookedUnits(fixture, night))
                .as("a paid-for room must never be released by the sweeper").isEqualTo(1);
    }

    /**
     * CONFIRMED -> COMPLETED is in the transition table and nothing else can reach it. An
     * unreachable state in a state machine is a defect, not an unused feature.
     */
    @Test
    void aConfirmedStayPastCheckoutIsCompleted() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Completion Hotel", 2);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(2), 1, 1, 0, null));
        confirm(booking);

        clock().advance(Duration.ofDays(5));
        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.bookingsCompleted()).isEqualTo(1);
        assertThat(state(booking)).isEqualTo(BookingState.COMPLETED);
    }

    @Test
    void aConfirmedStayStillRunningIsNotCompleted() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Ongoing Stay Hotel", 2);
        LocalDate night = fixture.firstNight();
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(5), 1, 1, 0, null));
        confirm(booking);

        clock().advance(Duration.ofDays(1));
        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.bookingsCompleted()).isZero();
        assertThat(state(booking)).isEqualTo(BookingState.CONFIRMED);
    }

    /**
     * Completion is judged in the property's timezone, not the server's.
     *
     * <p>The stay checks out on the 9th. The clock is then moved to 18:40Z on the 8th — still
     * the 8th in UTC, already the 9th in Asia/Kolkata. The stay is therefore over at the
     * hotel, and a server comparing against its own UTC date would leave it open for another
     * five and a half hours.
     */
    @Test
    void completionUsesThePropertysLocalDateNotTheServersDate() {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Timezone Completion Hotel", 2);
        LocalDate propertyToday = fixture.firstNight();
        assertThat(propertyToday)
                .as("fixture precondition: the property is already a day ahead of UTC")
                .isEqualTo(LocalDate.of(2026, 9, 8));

        // One night: checks in on the 8th, checks out on the 9th (property-local).
        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), propertyToday, propertyToday.plusDays(1), 1, 1, 0, null));
        confirm(booking);

        clock().advance(Duration.ofDays(1));

        assertThat(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC))
                .as("UTC has not reached the checkout date yet")
                .isEqualTo(LocalDate.of(2026, 9, 8));
        assertThat(LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Asia/Kolkata")))
                .as("but the property has")
                .isEqualTo(LocalDate.of(2026, 9, 9));

        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.bookingsCompleted())
                .as("checkout has passed in Asia/Kolkata, so the stay is complete there")
                .isEqualTo(1);
        assertThat(state(booking)).isEqualTo(BookingState.COMPLETED);
    }

    @Test
    void anEmptySweepReportsNothingAndChangesNothing() {
        clock().setTo(START);

        SweepResponse result = bookingSweeper.sweep();

        assertThat(result.holdsExpired()).isZero();
        assertThat(result.skippedDueToConcurrentChange()).isZero();
    }

    /** Drives the booking through the state machine the way payment will in the next phase. */
    private void confirm(BookingResponse response) {
        Booking booking = bookingRepository.findByBookingUid(response.bookingUid()).orElseThrow();
        booking.transitionTo(BookingState.PENDING_PAYMENT);
        booking.transitionTo(BookingState.CONFIRMED);
        bookingRepository.save(booking);
    }

    private BookingState state(BookingResponse response) {
        return bookingRepository.findByBookingUid(response.bookingUid()).orElseThrow().getState();
    }

    private int bookedUnits(Fixture fixture, LocalDate night) {
        return dailyInventoryRepository.findByRoomTypeIdAndStayDate(fixture.roomTypeId(), night)
                .orElseThrow().getBookedUnits();
    }
}
