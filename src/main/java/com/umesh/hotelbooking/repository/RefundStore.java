package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Refund;

import java.util.List;

/** Persistence port for {@link Refund}. */
public interface RefundStore {

    Refund save(Refund refund);

    List<Refund> findByBookingId(Long bookingId);
}
