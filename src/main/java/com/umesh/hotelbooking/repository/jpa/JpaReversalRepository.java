package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Reversal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA queries behind {@link JpaReversalStore}. Not injected outside this package. */
interface JpaReversalRepository extends JpaRepository<Reversal, Long> {

    List<Reversal> findByBookingId(Long bookingId);
}
