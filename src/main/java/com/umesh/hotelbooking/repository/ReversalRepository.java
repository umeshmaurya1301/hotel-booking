package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Reversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReversalRepository extends JpaRepository<Reversal, Long> {

    Optional<Reversal> findByReversalUid(String reversalUid);

    List<Reversal> findByBookingId(Long bookingId);
}
