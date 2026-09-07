package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.booking.Booking;
import com.umesh.hotelbooking.domain.model.booking.BookingState;
import com.umesh.hotelbooking.domain.vo.BookingId;
import com.umesh.hotelbooking.domain.vo.GuestId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link Booking}. Implemented in infrastructure; domain and application
 * code depend only on this interface.
 */
public interface BookingRepository {

    Booking save(Booking booking);

    Optional<Booking> findById(BookingId id);

    List<Booking> findByStateAndHoldExpiredBefore(BookingState state, Instant cutoff);

    List<Booking> findByGuestId(GuestId guestId);
}
