package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryLockProperties;
import com.umesh.hotelbooking.exception.InventoryLockTimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Lock handles are held for their release-on-close side effect and never referenced in the
// body, which is precisely what -Xlint:try flags. That is the pattern, not a mistake.
@SuppressWarnings("try")
class InventoryLockRegistryTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 10);

    private InventoryLockRegistry registry(boolean enabled, Duration timeout) {
        return new InventoryLockRegistry(new InventoryLockProperties(enabled, timeout));
    }

    private InventoryLockRegistry.LockKey key(long roomTypeId, LocalDate date) {
        return new InventoryLockRegistry.LockKey(roomTypeId, date);
    }

    /**
     * The one piece of in-memory atomicity this design actually depends on (design doc
     * 5.7.3). If two threads could receive two different lock instances for one key, the
     * whole scheme would be defeated silently while continuing to look like it worked.
     */
    @Test
    void concurrentCallersAlwaysReceiveTheSameLockInstanceForAKey() throws Exception {
        InventoryLockRegistry registry = registry(true, Duration.ofSeconds(1));
        InventoryLockRegistry.LockKey key = key(1L, DAY_1);
        int threads = 32;

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<ReentrantLock>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return registry.lockFor(key);
                }));
            }
            start.countDown();

            ReentrantLock first = futures.get(0).get(10, TimeUnit.SECONDS);
            for (Future<ReentrantLock> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isSameAs(first);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keysSortIntoATotalOrderByRoomTypeThenDate() {
        List<InventoryLockRegistry.LockKey> keys = new ArrayList<>(List.of(
                key(2L, DAY_1),
                key(1L, DAY_1.plusDays(2)),
                key(1L, DAY_1),
                key(1L, DAY_1.plusDays(1))));
        Collections.shuffle(keys);

        Collections.sort(keys);

        assertThat(keys).containsExactly(
                key(1L, DAY_1),
                key(1L, DAY_1.plusDays(1)),
                key(1L, DAY_1.plusDays(2)),
                key(2L, DAY_1));
    }

    @Test
    void acquiringAndClosingReleasesEveryLock() {
        InventoryLockRegistry registry = registry(true, Duration.ofSeconds(1));
        List<InventoryLockRegistry.LockKey> keys = List.of(key(1L, DAY_1), key(1L, DAY_1.plusDays(1)));

        try (InventoryLockRegistry.LockHandle handle = registry.acquireAll(keys)) {
            assertThat(handle).isNotNull();
            assertThat(registry.lockFor(keys.get(0)).isLocked()).isTrue();
        }

        // Reclaimed once uncontended, so the map does not grow as the horizon rolls forward.
        assertThat(registry.trackedKeyCount()).isZero();
    }

    /**
     * Never a bare {@code lock()}: an unbounded wait turns contention into a hung request
     * (design doc 5.4). A contended key must fail fast with a distinct, retryable outcome.
     */
    @Test
    void aContendedKeyTimesOutRatherThanBlockingForever() throws Exception {
        InventoryLockRegistry registry = registry(true, Duration.ofMillis(150));
        InventoryLockRegistry.LockKey key = key(1L, DAY_1);

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                try (InventoryLockRegistry.LockHandle ignored = registry.acquireAll(List.of(key))) {
                    held.countDown();
                    release.await();
                }
                return null;
            });
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> registry.acquireAll(List.of(key)))
                    .isInstanceOf(InventoryLockTimeoutException.class);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    /**
     * A failed acquisition must leave nothing held, or the next attempt deadlocks against
     * the remains of the previous one.
     */
    @Test
    void aTimeoutPartWayThroughReleasesTheLocksAlreadyTaken() throws Exception {
        InventoryLockRegistry registry = registry(true, Duration.ofMillis(150));
        InventoryLockRegistry.LockKey first = key(1L, DAY_1);
        InventoryLockRegistry.LockKey second = key(1L, DAY_1.plusDays(1));

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Another thread holds only the SECOND key, so our acquisition takes the first
            // and then times out on the second.
            executor.submit(() -> {
                try (InventoryLockRegistry.LockHandle ignored = registry.acquireAll(List.of(second))) {
                    held.countDown();
                    release.await();
                }
                return null;
            });
            assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> registry.acquireAll(List.of(first, second)))
                    .isInstanceOf(InventoryLockTimeoutException.class);

            assertThat(registry.lockFor(first).isLocked())
                    .as("the first key must not be left locked after the failure")
                    .isFalse();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void whenDisabledNoLockIsTakenAtAll() {
        InventoryLockRegistry registry = registry(false, Duration.ofSeconds(1));

        try (InventoryLockRegistry.LockHandle ignored = registry.acquireAll(List.of(key(1L, DAY_1)))) {
            assertThat(registry.trackedKeyCount()).isZero();
        }
    }
}
