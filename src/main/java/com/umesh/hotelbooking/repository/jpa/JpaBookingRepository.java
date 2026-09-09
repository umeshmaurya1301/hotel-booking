package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaBookingStore}. Not injected outside this package. */
interface JpaBookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingUid(String bookingUid);

    List<Booking> findByStateInAndHoldExpiresAtBefore(Collection<BookingState> states, Instant cutoff);

    List<Booking> findByStateAndCheckOutLessThanEqual(BookingState state, LocalDate cutoff);
}
