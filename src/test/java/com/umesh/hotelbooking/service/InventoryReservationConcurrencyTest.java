package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The tests everything else rests on.
 *
 * <p>Booking is a check-then-act, and a thread-safe collection does not fix a check-then-act:
 * {@code ConcurrentHashMap} makes individual operations atomic but does nothing for an
 * invariant spanning two of them. These tests exercise the mechanism that does fix it — a
 * conditional UPDATE whose predicate and mutation are one statement — under real concurrent
 * transactions, because a single-threaded test cannot tell a correct implementation from a
 * broken one here.
 *
 * <p>Deliberately not {@code @Transactional}: a test-managed transaction would enrol every
 * worker thread in one transaction and quietly serialise exactly what is being tested.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=30"
})
class InventoryReservationConcurrencyTest extends AbstractBookingConcurrencyTestSupport {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;

    /**
     * The single most valuable test in the project: 20 threads race for the last unit on one
     * room-night. Exactly one may win.
     */
    @Test
    void twentyThreadsRacingTheLastUnitProduceExactlyOneWinner() throws Exception {
        Fixture fixture = onboardRoomType("Race Hotel", 1);
        LocalDate night = fixture.firstNight();

        RaceResult result = race(20, () ->
                bookingService.create(freshMeta(), new CreateBookingRequest(
                        null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0, null)));

        assertThat(result.successes()).as("exactly one booking may win the last unit").isEqualTo(1);
        assertThat(result.inventoryUnavailable()).isEqualTo(19);
        assertThat(result.unexpected()).as("no unexpected failures: %s", result.unexpectedMessages()).isEmpty();

        DailyInventory row = inventory(fixture.roomTypeId(), night);
        assertThat(row.getBookedUnits()).isEqualTo(1);
        assertThat(row.getBookedUnits()).isLessThanOrEqualTo(row.getTotalUnits());
    }

    /**
     * Multi-unit is strictly more interesting than single-unit, and this is the test that
     * justifies modelling units at all: with 3 free and two-unit requests, a naive
     * implementation can let two requests through and allocate 4 — or allocate one request
     * partially. Both are correctness failures a single-unit test cannot detect.
     */
    @Test
    void concurrentMultiUnitRequestsNeverPartiallyAllocate() throws Exception {
        Fixture fixture = onboardRoomType("Multi Unit Hotel", 3);
        LocalDate night = fixture.firstNight();

        RaceResult result = race(4, () ->
                bookingService.create(freshMeta(), new CreateBookingRequest(
                        null, fixture.roomTypeUid(), night, night.plusDays(1), 2, 2, 0, null)));

        assertThat(result.successes()).as("3 units cannot satisfy two 2-unit bookings").isEqualTo(1);
        assertThat(result.inventoryUnavailable()).isEqualTo(3);
        assertThat(result.unexpected()).as("%s", result.unexpectedMessages()).isEmpty();

        DailyInventory row = inventory(fixture.roomTypeId(), night);
        assertThat(row.getBookedUnits())
                .as("2 units allocated in full, or none - never 3 (a partial allocation)")
                .isEqualTo(2);
    }

    /**
     * A booking that cannot get every night must get none. Rollback does this, so there is no
     * compensating-decrement code — and therefore no compensating-decrement bug.
     */
    @Test
    void aMultiNightBookingBlockedOnOneNightReservesNothing() {
        Fixture fixture = onboardRoomType("Atomicity Hotel", 1);
        LocalDate night1 = fixture.firstNight();
        LocalDate night2 = night1.plusDays(1);
        LocalDate night3 = night1.plusDays(2);

        // Take the single unit on the middle night only.
        bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night2, night3, 1, 1, 0, null));

        assertThatThrownBy(() -> bookingService.create(freshMeta(), new CreateBookingRequest(
                null, fixture.roomTypeUid(), night1, night3.plusDays(1), 1, 1, 0, null)))
                .isInstanceOf(InventoryUnavailableException.class)
                .hasMessageContaining(night2.toString());

        assertThat(inventory(fixture.roomTypeId(), night1).getBookedUnits())
                .as("the night before the blocked one must not stay reserved").isZero();
        assertThat(inventory(fixture.roomTypeId(), night2).getBookedUnits()).isEqualTo(1);
        assertThat(inventory(fixture.roomTypeId(), night3).getBookedUnits())
                .as("the night after the blocked one must never have been reserved").isZero();
    }

    /**
     * Deadlock avoidance. Each night's UPDATE holds its row lock until commit, so a
     * multi-night booking holds several at once; overlapping ranges taken in opposite orders
     * would deadlock, and the failure is load-dependent so it never shows up single-threaded.
     * Total ordering on (roomTypeId, stayDate) is what makes it impossible.
     *
     * <p>Two threads book overlapping ranges from opposite ends, repeatedly. Both must
     * complete — neither sacrificed to a deadlock, and no lock timeout escaping as a failure.
     */
    @Test
    void overlappingRangesBookedFromOppositeEndsAllComplete() throws Exception {
        Fixture fixture = onboardRoomType("Deadlock Hotel", 60);
        LocalDate d1 = fixture.firstNight();
        int iterations = 12;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);

            Callable<Integer> ascending = () -> {
                start.await();
                int done = 0;
                for (int i = 0; i < iterations; i++) {
                    bookingService.create(freshMeta(), new CreateBookingRequest(
                            null, fixture.roomTypeUid(), d1, d1.plusDays(3), 1, 1, 0, null));
                    done++;
                }
                return done;
            };
            // Same overlapping nights, entered from the far end of the range.
            Callable<Integer> descending = () -> {
                start.await();
                int done = 0;
                for (int i = 0; i < iterations; i++) {
                    bookingService.create(freshMeta(), new CreateBookingRequest(
                            null, fixture.roomTypeUid(), d1.plusDays(2), d1.plusDays(5), 1, 1, 0, null));
                    done++;
                }
                return done;
            };

            Future<Integer> first = executor.submit(ascending);
            Future<Integer> second = executor.submit(descending);
            start.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(iterations);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(iterations);
        } finally {
            executor.shutdownNow();
        }

        // The shared night carries both threads' bookings; nothing was lost or double-counted.
        assertThat(inventory(fixture.roomTypeId(), d1.plusDays(2)).getBookedUnits())
                .isEqualTo(iterations * 2);
        assertThat(inventory(fixture.roomTypeId(), d1).getBookedUnits()).isEqualTo(iterations);
        assertThat(inventory(fixture.roomTypeId(), d1.plusDays(4)).getBookedUnits()).isEqualTo(iterations);
    }

    /**
     * Sustained mixed contention: many threads, several nights, limited units. The invariant
     * that must survive is not "everyone succeeds" but "no row is ever over its capacity".
     */
    @Test
    void underSustainedContentionNoRowEverExceedsItsCapacity() throws Exception {
        Fixture fixture = onboardRoomType("Contention Hotel", 8);
        LocalDate d1 = fixture.firstNight();
        AtomicInteger offsets = new AtomicInteger();

        // Threads take staggered but overlapping two-night ranges, so they contend on shared
        // nights rather than each having the room to itself.
        RaceResult result = race(24, () -> {
            LocalDate start = d1.plusDays(offsets.getAndIncrement() % 3);
            return bookingService.create(freshMeta(), new CreateBookingRequest(
                    null, fixture.roomTypeUid(), start, start.plusDays(2), 1, 1, 0, null));
        });

        assertThat(result.unexpected()).as("%s", result.unexpectedMessages()).isEmpty();

        List<DailyInventory> rows = dailyInventoryStore.findByRoomTypeIdAndStayDateBetween(
                fixture.roomTypeId(), d1, d1.plusDays(6));
        for (DailyInventory row : rows) {
            assertThat(row.getBookedUnits())
                    .as("night %s must never exceed capacity", row.getStayDate())
                    .isBetween(0, row.getTotalUnits());
        }
    }

    private DailyInventory inventory(Long roomTypeId, LocalDate night) {
        return dailyInventoryStore.findByRoomTypeIdAndStayDate(roomTypeId, night).orElseThrow();
    }

    /**
     * Releases every thread at once with a latch, so they genuinely collide rather than
     * arriving in a staggered line that any implementation would survive.
     */
    private RaceResult race(int threads, Callable<?> action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        List<String> unexpected = new ArrayList<>();

        try {
            List<Future<?>> futures = new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        action.call();
                        successes.incrementAndGet();
                    } catch (InventoryUnavailableException e) {
                        unavailable.incrementAndGet();
                    } catch (Exception e) {
                        synchronized (unexpected) {
                            unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                    return null;
                }));
            }

            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        return new RaceResult(successes.get(), unavailable.get(), List.copyOf(unexpected));
    }

    private record RaceResult(int successes, int inventoryUnavailable, List<String> unexpected) {
        String unexpectedMessages() {
            return String.join(" | ", unexpected);
        }
    }
}
