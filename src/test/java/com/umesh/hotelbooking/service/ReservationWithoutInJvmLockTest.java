package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the last-unit race with the in-JVM lock <b>switched off</b>.
 *
 * <p>This is the test that proves where correctness actually lives. The heap lock of design
 * doc 5.2.4 layer 1 is a throughput optimisation — it stops contenders wasting a database
 * round trip — and the document claims it "could be removed without affecting correctness".
 * That claim is worth nothing unless something checks it. With the lock disabled, every
 * thread reaches the database simultaneously and only the conditional UPDATE and the check
 * constraint stand between them and an overbooking. Exactly one still wins.
 *
 * <p>Presenting the lock as the safety mechanism would be overstating it; this asserts the
 * honest version.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "inventory.lock.enabled=false",
        "booking.sweeper.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=30"
})
class ReservationWithoutInJvmLockTest extends AbstractBookingConcurrencyTestSupport {

    @Autowired
    private BookingService bookingService;
    @Autowired
    private DailyInventoryRepository dailyInventoryRepository;
    @Autowired
    private InventoryLockRegistry lockRegistry;

    @Test
    void theDatabaseAloneStillPermitsExactlyOneWinner() throws Exception {
        Fixture fixture = onboardRoomType("No JVM Lock Hotel", 1);
        LocalDate night = fixture.firstNight();
        int threads = 20;

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
                        bookingService.create(freshMeta(), new CreateBookingRequest(
                                null, fixture.roomTypeUid(), night, night.plusDays(1), 1, 1, 0));
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

        assertThat(successes.get()).as("the conditional UPDATE alone must admit exactly one").isEqualTo(1);
        assertThat(unavailable.get()).isEqualTo(threads - 1);
        assertThat(unexpected).as("%s", unexpected).isEmpty();

        DailyInventory row = dailyInventoryRepository
                .findByRoomTypeIdAndStayDate(fixture.roomTypeId(), night).orElseThrow();
        assertThat(row.getBookedUnits()).isEqualTo(1);

        assertThat(lockRegistry.trackedKeyCount())
                .as("no heap locks are created at all when the registry is disabled")
                .isZero();
    }
}
