package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Reversal;

import java.util.List;

/** Persistence port for {@link Reversal}, the compensating action behind a settled payment that
 * could not be honoured. */
public interface ReversalStore {

    Reversal save(Reversal reversal);

    List<Reversal> findByBookingId(Long bookingId);
}
