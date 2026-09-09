package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.DailyInventory;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for room-night inventory — <b>the most important contract in the system</b>
 * (design doc 5.2.1).
 *
 * <p>{@link #reserveUnits} and {@link #releaseUnits} are stated here as guarantees an
 * implementation must honour, not as a description of how the JPA adapter honours them. That
 * separation is the point of this port: the no-double-booking guarantee belongs to the domain,
 * and a store either satisfies it or is not a valid implementation. A relational adapter will
 * reach for a single conditional {@code UPDATE}; a document store would reach for a conditional
 * write with a predicate on the same fields; an in-memory store would reach for a compare-and-set
 * on the row. All three are legitimate, and none of them is visible from here.
 */
public interface DailyInventoryStore {

    DailyInventory save(DailyInventory inventory);

    List<DailyInventory> saveAll(Iterable<DailyInventory> rows);

    /** @see BookingStore#saveAndFlush */
    DailyInventory saveAndFlush(DailyInventory inventory);

    Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);

    /** {@code from} and {@code to} are both inclusive. */
    List<DailyInventory> findByRoomTypeIdAndStayDateBetween(Long roomTypeId, LocalDate from, LocalDate to);

    /**
     * The batched form {@code AvailabilityFilter} needs (design doc 10, task spec §5.2): one
     * call covering every surviving room type across every surviving search candidate, rather
     * than one call to {@link #findByRoomTypeIdAndStayDateBetween} per room type — with 40
     * candidate properties averaging 3 room types each, that is 120 round trips against 1.
     * Implementations must satisfy it without fanning out internally, or the batching is a lie.
     * {@code from}/{@code to} are inclusive, matching the single-id form.
     */
    List<DailyInventory> findByRoomTypeIdInAndStayDateBetween(Collection<Long> roomTypeIds,
                                                              LocalDate from,
                                                              LocalDate to);

    long countByRoomTypeId(Long roomTypeId);

    /**
     * Reserves {@code units} on one room-night, <b>atomically</b>.
     *
     * <p>Booking is a check-then-act: read availability, then reserve. Two threads can both pass
     * the check and both reserve. The contract here removes the sequence rather than guarding
     * it — an implementation must evaluate "is there room for {@code units}?" and apply the
     * increment as one indivisible operation, with no window in which a competing caller can
     * observe the old count and act on it.
     *
     * <p><b>Returns 1 when the units were reserved and 0 when there was not enough
     * availability.</b> The count is the answer: not an exception, and not something the caller
     * recomputes for itself. Callers must treat 0 as failure. Returning a count rather than a
     * boolean keeps the contract honest about what a store actually reports, and matches the row
     * count a relational implementation already has in hand.
     *
     * <p>An implementation must never let {@code bookedUnits} exceed {@code totalUnits}, and is
     * expected to enforce that at the store level too, not only in this method — application
     * logic can be wrong, a constraint cannot be bypassed.
     */
    int reserveUnits(Long roomTypeId, LocalDate stayDate, int units);

    /**
     * Releases {@code units} on one room-night, used when a hold lapses or a booking ends.
     *
     * <p>Symmetric to {@link #reserveUnits} and subject to the same atomicity requirement.
     * Releasing more units than are held must be a no-op returning 0 rather than driving the
     * counter negative — a double release is a bug worth surfacing, but corrupting the counter
     * while surfacing it would be worse.
     */
    int releaseUnits(Long roomTypeId, LocalDate stayDate, int units);
}
