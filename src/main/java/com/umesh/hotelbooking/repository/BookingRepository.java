package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The DAO for {@link Booking}. Spring Data JPA generates the implementation at runtime — no
 * hand-written implementation class needed.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingUid(String bookingUid);

    List<Booking> findByStateAndHoldExpiresAtBefore(BookingState state, Instant cutoff);

    /** Sweeper scan for lapsed holds: both CREATED and PENDING_PAYMENT can expire (4.4). */
    List<Booking> findByStateInAndHoldExpiresAtBefore(Collection<BookingState> states, Instant cutoff);

    /**
     * Sweeper scan for completable stays. Takes a coarse cutoff and returns a superset: the
     * precise test is {@code checkOut < today} in each <em>property's own</em> timezone, and
     * that cannot be expressed in one query across properties in different zones. The caller
     * filters exactly; this just avoids scanning every confirmed booking ever made.
     */
    List<Booking> findByStateAndCheckOutLessThanEqual(BookingState state, LocalDate cutoff);

    List<Booking> findByGuestId(Long guestId);
}
