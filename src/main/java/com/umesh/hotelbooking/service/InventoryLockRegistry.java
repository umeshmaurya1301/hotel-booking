package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryLockProperties;
import com.umesh.hotelbooking.exception.InventoryLockTimeoutException;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Layer 1 of the concurrency defence: an in-JVM lock per room-night (design doc 5.2.4).
 *
 * <p><b>This is not the correctness mechanism.</b> It serialises threads contending for the
 * same room-night <em>before</em> they reach the database, so they do not all issue an UPDATE
 * that returns zero rows, and it bounds how many threads sit mid-transaction on one key.
 * Correctness comes from the atomic conditional UPDATE and the check constraint beneath it,
 * which hold across instances, restarts and JVMs — none of which a heap lock does. Disabling
 * this must not permit an overbooking, and a test asserts exactly that.
 */
@Component
public class InventoryLockRegistry {

    private final ConcurrentHashMap<LockKey, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final InventoryLockProperties properties;

    public InventoryLockRegistry(InventoryLockProperties properties) {
        this.properties = properties;
    }

    /**
     * Identity of one room-night. {@link Comparable} is load-bearing: acquiring locks in a
     * total order is what makes circular wait — and therefore deadlock — structurally
     * impossible when a multi-night booking holds several at once (design doc 5.3).
     *
     * <p>Deliberately local to this class rather than a shared domain type. Nothing outside
     * the reservation path needs to name a room-night.
     */
    public record LockKey(Long roomTypeId, LocalDate stayDate) implements Comparable<LockKey> {

        private static final Comparator<LockKey> ORDER =
                Comparator.comparing(LockKey::roomTypeId).thenComparing(LockKey::stayDate);

        @Override
        public int compareTo(LockKey other) {
            return ORDER.compare(this, other);
        }
    }

    /** Released by {@link #releaseAll}; an {@link AutoCloseable} so callers cannot forget. */
    public final class LockHandle implements AutoCloseable {

        private final List<LockKey> held;

        private LockHandle(List<LockKey> held) {
            this.held = held;
        }

        @Override
        public void close() {
            releaseAll(held);
        }
    }

    /**
     * Acquires every key in ascending order, or none at all.
     *
     * <p>{@code tryLock} with a timeout, never a bare {@code lock()}: an unbounded wait turns
     * contention into a hung request (design doc 5.4). On timeout the locks already taken are
     * released before throwing, so a failed acquisition leaves nothing held.
     *
     * <p>The locks are fair, so a thread cannot be starved indefinitely under sustained
     * contention on a popular room-night.
     */
    public LockHandle acquireAll(List<LockKey> keys) {
        if (!properties.enabled()) {
            return new LockHandle(List.of());
        }

        List<LockKey> ordered = keys.stream().sorted().distinct().toList();
        List<LockKey> acquired = new ArrayList<>(ordered.size());
        long timeoutMillis = properties.timeout().toMillis();

        for (LockKey key : ordered) {
            ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock(true));
            boolean locked;
            try {
                locked = lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                releaseAll(acquired);
                throw new InventoryLockTimeoutException(key.roomTypeId(), key.stayDate());
            }
            if (!locked) {
                releaseAll(acquired);
                throw new InventoryLockTimeoutException(key.roomTypeId(), key.stayDate());
            }
            acquired.add(key);
        }
        return new LockHandle(acquired);
    }

    private void releaseAll(List<LockKey> keys) {
        // Reverse order: unwinding the way it was wound keeps the hold windows nested.
        for (int i = keys.size() - 1; i >= 0; i--) {
            LockKey key = keys.get(i);
            ReentrantLock lock = locks.get(key);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            reclaimIfIdle(key);
        }
    }

    /**
     * Drops the map entry once nobody holds or wants the lock, so the map does not grow
     * without bound as the inventory horizon rolls forward.
     *
     * <p>There is a benign race here: a thread that already holds a reference to this lock
     * object, but has not yet called {@code tryLock}, could end up locking an instance that
     * has just been removed while a later thread creates a fresh one for the same key — two
     * threads then briefly "hold" the same room-night. That costs a wasted database round
     * trip, nothing more, because this lock is an optimisation and the conditional UPDATE is
     * what actually prevents overbooking. Paying for a correct fix here would mean adding
     * reference counting to a component that is explicitly not load-bearing.
     */
    private void reclaimIfIdle(LockKey key) {
        locks.compute(key, (k, lock) ->
                (lock == null || lock.isLocked() || lock.hasQueuedThreads()) ? lock : null);
    }

    /** Visible for tests: how many keys currently have a lock object. */
    public int trackedKeyCount() {
        return locks.size();
    }

    /**
     * Visible for tests. {@link ConcurrentHashMap#computeIfAbsent} being atomic is the one
     * piece of in-memory atomicity this design genuinely depends on: two threads must never
     * receive two different lock instances for the same key, which would silently defeat the
     * whole scheme while appearing to work (design doc 5.7.3).
     */
    public ReentrantLock lockFor(LockKey key) {
        return locks.computeIfAbsent(key, k -> new ReentrantLock(true));
    }
}
