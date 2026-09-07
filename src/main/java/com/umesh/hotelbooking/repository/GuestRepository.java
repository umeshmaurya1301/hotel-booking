package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    Optional<Guest> findByGuestUid(String guestUid);
}
