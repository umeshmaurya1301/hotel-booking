package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Guest;

import java.util.Optional;

/** Persistence port for {@link Guest}. */
public interface GuestStore {

    Guest save(Guest guest);

    /** @see BookingStore#saveAndFlush */
    Guest saveAndFlush(Guest guest);

    Optional<Guest> findById(Long id);

    Optional<Guest> findByGuestUid(String guestUid);
}
