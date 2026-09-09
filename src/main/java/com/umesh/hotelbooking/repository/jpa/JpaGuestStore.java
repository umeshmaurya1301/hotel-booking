package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.repository.GuestStore;

import java.util.Optional;

/** JPA adapter for {@link GuestStore}. */
class JpaGuestStore implements GuestStore {

    private final JpaGuestRepository repository;

    JpaGuestStore(JpaGuestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Guest save(Guest guest) {
        return repository.save(guest);
    }

    @Override
    public Guest saveAndFlush(Guest guest) {
        return repository.saveAndFlush(guest);
    }

    @Override
    public Optional<Guest> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Guest> findByGuestUid(String guestUid) {
        return repository.findByGuestUid(guestUid);
    }
}
