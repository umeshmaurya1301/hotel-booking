package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Reserves and releases room-nights. The single most important class in the system.
 *
 * <p>Three properties hold here, and each is deliberate:
 *
 * <p><b>Atomic per night.</b> Each night is one conditional UPDATE
 * ({@code DailyInventoryRepository#reserveUnits}) whose predicate and mutation are the same
 * statement, so there is no read-then-write window. A row count of 0 means insufficient
 * availability; there is no separate availability check that could go stale between reading
 * and writing.
 *
 * <p><b>All-or-nothing across nights.</b> A three-night booking issues three UPDATEs inside
 * one transaction. If night two comes back with 0 rows, the exception rolls the transaction
 * back and nights one and three are released by that rollback — there is no compensating
 * decrement to write, and therefore no compensating decrement to get wrong. A booking that
 * cannot get every night gets none: a partial allocation would be a correctness failure, not
 * a degraded success.
 *
 * <p><b>Totally ordered.</b> Nights are always taken in ascending {@code (roomTypeId,
 * stayDate)} order. Each UPDATE holds its row lock until the transaction commits, so a
 * multi-night booking holds several at once; two bookings taking overlapping ranges from
 * opposite ends would deadlock. Sorting first makes circular wait structurally impossible.
 * The atomic UPDATE did not remove this risk, it moved it into the database — this is what
 * addresses it there.
 */
@Service
public class InventoryReservationService {

    private static final Logger log = LoggerFactory.getLogger(InventoryReservationService.class);

    private final DailyInventoryRepository dailyInventoryRepository;
    private final InventoryLockRegistry lockRegistry;

    public InventoryReservationService(DailyInventoryRepository dailyInventoryRepository,
                                       InventoryLockRegistry lockRegistry) {
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.lockRegistry = lockRegistry;
    }

    /**
     * Reserves {@code units} on every night, or throws and reserves none.
     *
     * <p>Joins the caller's transaction ({@code MANDATORY}) rather than starting its own: the
     * all-or-nothing guarantee is the caller's transaction rolling back, so being called
     * outside one would silently break it. Failing loudly beats reserving nights that nothing
     * will ever release.
     *
     * @throws InventoryUnavailableException naming the first night that could not be
     *     satisfied, so the caller can re-search intelligently rather than retrying blindly
     */
    // The lock handle is held for its side effect - release on close - and never referenced
    // in the body, which is exactly what -Xlint:try flags. That is the point of the pattern.
    @SuppressWarnings("try")
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long roomTypeId, String roomTypeUid, List<LocalDate> nights, int units) {
        List<LocalDate> ordered = nights.stream().sorted().toList();
        List<InventoryLockRegistry.LockKey> keys = ordered.stream()
                .map(night -> new InventoryLockRegistry.LockKey(roomTypeId, night))
                .toList();

        // The in-JVM lock wraps only the UPDATEs - never any I/O, and never a gateway call.
        try (InventoryLockRegistry.LockHandle ignored = lockRegistry.acquireAll(keys)) {
            for (LocalDate night : ordered) {
                int rowsAffected = dailyInventoryRepository.reserveUnits(roomTypeId, night, units);
                if (rowsAffected == 0) {
                    // Rolls back the nights already taken in this transaction.
                    throw new InventoryUnavailableException(roomTypeUid, night);
                }
            }
        }
    }

    /**
     * Releases {@code units} on every night. Used when a hold lapses, and by cancellation and
     * reversal in later phases.
     *
     * <p>Ordered identically to {@link #reserve}: releases take the same row locks, so they
     * are subject to the same deadlock risk and follow the same discipline.
     *
     * <p>A night that releases 0 rows is logged rather than thrown on. Release runs on
     * cleanup paths — a sweeper, a cancellation — where aborting halfway would leave the
     * remaining nights held forever. Best effort across all nights, with the anomaly
     * recorded, is the more useful behaviour.
     */
    @SuppressWarnings("try")
    @Transactional(propagation = Propagation.MANDATORY)
    public int release(Long roomTypeId, List<LocalDate> nights, int units) {
        List<LocalDate> ordered = nights.stream().sorted().toList();
        List<InventoryLockRegistry.LockKey> keys = ordered.stream()
                .map(night -> new InventoryLockRegistry.LockKey(roomTypeId, night))
                .toList();

        int released = 0;
        try (InventoryLockRegistry.LockHandle ignored = lockRegistry.acquireAll(keys)) {
            for (LocalDate night : ordered) {
                int rowsAffected = dailyInventoryRepository.releaseUnits(roomTypeId, night, units);
                if (rowsAffected == 0) {
                    log.warn("Release affected no rows: roomTypeId={} night={} units={} "
                                    + "(already released, or the row is missing)",
                            roomTypeId, night, units);
                } else {
                    released++;
                }
            }
        }
        return released;
    }
}
