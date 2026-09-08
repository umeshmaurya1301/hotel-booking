package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.DailyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyInventoryRepository extends JpaRepository<DailyInventory, Long> {

    Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);

    /** {@code from} and {@code to} are both inclusive, matching JPA's BETWEEN semantics. */
    List<DailyInventory> findByRoomTypeIdAndStayDateBetween(Long roomTypeId, LocalDate from, LocalDate to);

    /**
     * The batched form {@code AvailabilityFilter} needs (design doc 10, task spec §5.2): one
     * query for every surviving room type across every surviving search candidate, instead of
     * one call to {@link #findByRoomTypeIdAndStayDateBetween} per room type — with 40
     * candidate properties averaging 3 room types each, that difference is 120 queries against
     * 1 for a single search. {@code from}/{@code to} are inclusive, matching the single-id form.
     */
    List<DailyInventory> findByRoomTypeIdInAndStayDateBetween(Collection<Long> roomTypeIds, LocalDate from, LocalDate to);

    /**
     * The last night already materialised for a room type — the starting point for rolling
     * the horizon forward without re-creating rows that already exist.
     */
    @Query("select max(d.stayDate) from DailyInventory d where d.roomTypeId = :roomTypeId")
    Optional<LocalDate> findLastMaterialisedDate(@Param("roomTypeId") Long roomTypeId);

    long countByRoomTypeId(Long roomTypeId);

    /**
     * Reserves {@code units} on one room-night, atomically. <b>This single statement is the
     * correctness mechanism of the whole system</b> (design doc 5.2.1).
     *
     * <p>Booking is a check-then-act: read availability, then reserve. Two threads can both
     * pass the check and both reserve. The fix here is not to guard the sequence with a lock
     * but to remove the sequence: the predicate {@code booked_units + :units <= total_units}
     * and the mutation {@code booked_units = booked_units + :units} are the same statement,
     * evaluated under the row lock the database takes to perform the write. There is no
     * read-then-write window for a competing transaction to slip into.
     *
     * <p>Returns 1 when the units were reserved and <b>0 when there was not enough
     * availability</b> — the row count is the answer, not an exception and not a value the
     * application compares for itself. Callers must treat 0 as failure.
     *
     * <p>Deliberately not {@code SELECT ... FOR UPDATE} then write: that is two round trips
     * instead of one, holds its lock across the application's decision rather than for the
     * duration of a statement, and puts correctness in the hands of code that could forget
     * the lock.
     *
     * <p>{@code flushAutomatically} pushes pending changes before the update so this
     * statement sees them. The persistence context is deliberately <em>not</em> cleared:
     * callers read prices before reserving and never re-read these rows afterwards in the
     * same transaction, so no stale copy is ever consulted.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DailyInventory d
               set d.bookedUnits = d.bookedUnits + :units
             where d.roomTypeId = :roomTypeId
               and d.stayDate = :stayDate
               and d.bookedUnits + :units <= d.totalUnits
            """)
    int reserveUnits(@Param("roomTypeId") Long roomTypeId,
                     @Param("stayDate") LocalDate stayDate,
                     @Param("units") int units);

    /**
     * Releases {@code units} on one room-night, used when a hold lapses or a booking ends.
     *
     * <p>The {@code booked_units >= :units} guard makes a double release a no-op returning 0
     * rather than driving the counter negative — releasing twice is a bug worth surfacing,
     * but corrupting the counter while surfacing it would be worse.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update DailyInventory d
               set d.bookedUnits = d.bookedUnits - :units
             where d.roomTypeId = :roomTypeId
               and d.stayDate = :stayDate
               and d.bookedUnits >= :units
            """)
    int releaseUnits(@Param("roomTypeId") Long roomTypeId,
                     @Param("stayDate") LocalDate stayDate,
                     @Param("units") int units);
}
