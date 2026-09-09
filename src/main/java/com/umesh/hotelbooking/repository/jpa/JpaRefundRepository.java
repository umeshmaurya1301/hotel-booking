package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA queries behind {@link JpaRefundStore}. Not injected outside this package. */
interface JpaRefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByBookingId(Long bookingId);
}
