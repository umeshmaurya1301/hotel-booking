package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaGuestStore}. Not injected outside this package. */
interface JpaGuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByGuestUid(String guestUid);
}
