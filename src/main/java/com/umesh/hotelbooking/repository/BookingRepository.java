package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The DAO for {@link Booking}. Spring Data JPA generates the implementation at runtime — no
 * hand-written implementation class needed.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingUid(String bookingUid);

    List<Booking> findByStateAndHoldExpiresAtBefore(BookingState state, Instant cutoff);

    List<Booking> findByGuestId(Long guestId);
}
