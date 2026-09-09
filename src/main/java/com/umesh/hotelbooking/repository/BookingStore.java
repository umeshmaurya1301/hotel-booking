package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Booking}. See this package's {@code package-info} for why the
 * ports name no Spring Data type. */
public interface BookingStore {

    Booking save(Booking booking);

    /**
     * Saves and makes the write visible to subsequent reads in the same transaction, surfacing
     * any store-level constraint violation <em>now</em> rather than at commit.
     *
     * <p>This is a contract about visibility and failure timing, not a JPA flush in disguise:
     * a store that batches writes must stop batching for this call, and a store with deferred
     * constraint checking must force them. Callers use it where the next statement depends on
     * the row being there, and tests use it where the point of the test is that the store
     * rejects the write.
     */
    Booking saveAndFlush(Booking booking);

    Optional<Booking> findById(Long id);

    Optional<Booking> findByBookingUid(String bookingUid);

    /** Sweeper scan for lapsed holds: both CREATED and PENDING_PAYMENT can expire (4.4). */
    List<Booking> findByStateInAndHoldExpiresAtBefore(Collection<BookingState> states, Instant cutoff);

    /**
     * Sweeper scan for completable stays. Takes a coarse cutoff and returns a superset: the
     * precise test is {@code checkOut < today} in each <em>property's own</em> timezone, and
     * that cannot be expressed in one query across properties in different zones. The caller
     * filters exactly; this just avoids scanning every confirmed booking ever made.
     */
    List<Booking> findByStateAndCheckOutLessThanEqual(BookingState state, LocalDate cutoff);

    void deleteAll();
}
