package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByRefundUid(String refundUid);

    List<Refund> findByBookingId(Long bookingId);
}
