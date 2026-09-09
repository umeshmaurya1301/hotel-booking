package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.exception.InvalidPaymentStateException;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 15.1 test 6, design doc 4.4's optimistic-lock race made real: a payment settling
 * concurrently with the sweep that would expire its booking's hold. {@link Booking#getVersion}
 * is the mechanism under test - two threads racing the same row, released together with a
 * {@link CountDownLatch} exactly as {@code InventoryReservationConcurrencyTest} already does
 * for the reservation races, rather than the single-threaded "payment already won" scenario
 * {@code BookingSweeperTest} covers deterministically.
 *
 * <p>Only the aggregate invariant is asserted, never which thread wins - the task spec is
 * explicit that asserting a winner is how this kind of test becomes flaky. The race runs for a
 * bounded number of iterations so the invariant is shown to hold under real contention, not
 * merely on one lucky interleaving.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.hold-ttl=15m",
        "booking.sweeper.enabled=false",
        "payment.reconciliation.enabled=false"
})
class SweeperPaymentRaceTest extends AbstractBookingConcurrencyTestSupport {

    private static final Instant START = Instant.parse("2026-09-08T00:00:00Z");
    private static final int ITERATIONS = 15;

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
    private PaymentService paymentService;
    @Autowired
    private BookingSweeper bookingSweeper;
    @Autowired
    private com.umesh.hotelbooking.repository.BookingStore bookingStore;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    @Test
    void exactlyOneOfExpiredOrConfirmedOccursAndInventoryIsNeverDoubleReleasedOrLeftHeldByAConfirmedBooking()
            throws Exception {
        for (int i = 0; i < ITERATIONS; i++) {
            runOneRace(i);
        }
    }

    private void runOneRace(int iteration) throws Exception {
        clock().setTo(START);
        Fixture fixture = onboardRoomType("Sweeper Race Hotel " + iteration, 1);
        LocalDate night = fixture.firstNight();

        BookingResponse booking = bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null));
        assertThat(inventory(fixture, night).getBookedUnits()).isEqualTo(1);

        // Past booking.hold-ttl: both the sweeper and the payment now consider this booking
        // eligible to act on, so whichever transaction commits first determines the outcome.
        clock().advance(Duration.ofMinutes(16));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicReference<Exception> paymentException = new AtomicReference<>();
        List<Exception> unexpected = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Runnable sweep = () -> {
                ready.countDown();
                await(start);
                bookingSweeper.sweep();
            };
            Runnable pay = () -> {
                ready.countDown();
                await(start);
                try {
                    paymentService.pay(booking.bookingUid(),
                            new RequestMeta(UUID.randomUUID().toString(), ApiType.PAY_BOOKING, UUID.randomUUID().toString()),
                            new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.SETTLED));
                } catch (OptimisticLockingFailureException e) {
                    // The sweeper committed after this transaction had already read the
                    // booking: the version has moved by the time this one tries to flush.
                    paymentException.set(e);
                } catch (InvalidPaymentStateException e) {
                    // The sweeper committed *before* this transaction read the booking at all,
                    // so the payable-state guard rejected it up front rather than letting it
                    // reach a doomed flush. A second, equally legitimate way to lose this race
                    // - and the one that only appears when the sweeper wins by a wide enough
                    // margin, which is why it took a heavily loaded machine to surface it (see
                    // DESIGN.md 16.9).
                    paymentException.set(e);
                } catch (Exception e) {
                    synchronized (unexpected) {
                        unexpected.add(e);
                    }
                }
            };

            var sweepFuture = executor.submit(sweep);
            var payFuture = executor.submit(pay);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            sweepFuture.get(30, TimeUnit.SECONDS);
            payFuture.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(unexpected)
                .as("the only permitted failures are the two ways payment can lose this race: %s", unexpected)
                .isEmpty();

        Booking reloaded = bookingStore.findByBookingUid(booking.bookingUid()).orElseThrow();
        int bookedUnits = inventory(fixture, night).getBookedUnits();

        if (reloaded.getState() == BookingState.CONFIRMED) {
            assertThat(paymentException.get())
                    .as("payment won the race, so it must not also have failed").isNull();
            assertThat(bookedUnits)
                    .as("a CONFIRMED booking's inventory must never have been released").isEqualTo(1);
        } else if (reloaded.getState() == BookingState.EXPIRED) {
            assertThat(paymentException.get())
                    .as("the sweeper won, so the payment must have lost - silently succeeding "
                            + "against an expired booking would be the actual corruption")
                    .isNotNull();
            assertThat(bookedUnits)
                    .as("the sweeper won and released inventory exactly once").isZero();
        } else {
            throw new AssertionError("Booking ended in an unexpected state: " + reloaded.getState());
        }
    }

    private DailyInventory inventory(Fixture fixture, LocalDate night) {
        return dailyInventoryStore.findByRoomTypeIdAndStayDate(fixture.roomTypeId(), night).orElseThrow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
