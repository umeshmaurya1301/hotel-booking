package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Refund;
import com.umesh.hotelbooking.repository.RefundStore;

import java.util.List;

/** JPA adapter for {@link RefundStore}. */
class JpaRefundStore implements RefundStore {

    private final JpaRefundRepository repository;

    JpaRefundStore(JpaRefundRepository repository) {
        this.repository = repository;
    }

    @Override
    public Refund save(Refund refund) {
        return repository.save(refund);
    }

    @Override
    public List<Refund> findByBookingId(Long bookingId) {
        return repository.findByBookingId(bookingId);
    }
}
