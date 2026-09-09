package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.repository.ReversalStore;

import java.util.List;

/** JPA adapter for {@link ReversalStore}. */
class JpaReversalStore implements ReversalStore {

    private final JpaReversalRepository repository;

    JpaReversalStore(JpaReversalRepository repository) {
        this.repository = repository;
    }

    @Override
    public Reversal save(Reversal reversal) {
        return repository.save(reversal);
    }

    @Override
    public List<Reversal> findByBookingId(Long bookingId) {
        return repository.findByBookingId(bookingId);
    }
}
