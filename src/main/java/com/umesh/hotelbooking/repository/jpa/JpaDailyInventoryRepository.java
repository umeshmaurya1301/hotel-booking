package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.DailyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA queries behind {@link JpaDailyInventoryStore}. Not injected outside this
 * package.
 *
 * <p>The two {@code @Modifying} statements here are how <em>this</em> store honours
 * {@link com.umesh.hotelbooking.repository.DailyInventoryStore}'s atomicity contract. They are
 * not the contract itself — that is stated on the port, in terms a non-relational store could
 * also satisfy.
 */
interface JpaDailyInventoryRepository extends JpaRepository<DailyInventory, Long> {

    Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);

    /** {@code from} and {@code to} are both inclusive, matching JPA's BETWEEN semantics. */
    List<DailyInventory> findByRoomTypeIdAndStayDateBetween(Long roomTypeId, LocalDate from, LocalDate to);

    /** One query for every surviving room type across every surviving search candidate — the
     * port's batching requirement, satisfied by an {@code IN} rather than by a loop. */
    List<DailyInventory> findByRoomTypeIdInAndStayDateBetween(Collection<Long> roomTypeIds,
                                                              LocalDate from,
                                                              LocalDate to);

    long countByRoomTypeId(Long roomTypeId);

    /**
     * The relational answer to {@code DailyInventoryStore#reserveUnits}: the predicate
     * {@code booked_units + :units <= total_units} and the mutation
     * {@code booked_units = booked_units + :units} are <b>the same statement</b>, evaluated
     * under the row lock the database takes to perform the write. There is no read-then-write
     * window for a competing transaction to slip into, so the port's "no observable window"
     * requirement is met by construction rather than by discipline.
     *
     * <p>Deliberately not {@code SELECT ... FOR UPDATE} then write: that is two round trips
     * instead of one, holds its lock across the application's decision rather than for the
     * duration of a statement, and puts correctness in the hands of code that could forget the
     * lock.
     *
     * <p>{@code flushAutomatically} pushes pending changes before the update so this statement
     * sees them. The persistence context is deliberately <em>not</em> cleared: callers read
     * prices before reserving and never re-read these rows afterwards in the same transaction,
     * so no stale copy is ever consulted.
     *
     * <p>The row count this returns is what the port promises its callers — 1 reserved, 0 not
     * enough availability — which is why the adapter forwards it untouched rather than
     * translating it into a boolean or an exception.
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
     * The relational answer to {@code DailyInventoryStore#releaseUnits}. The
     * {@code booked_units >= :units} guard makes a double release a no-op returning 0 rather
     * than driving the counter negative, which is the port's stated requirement.
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
