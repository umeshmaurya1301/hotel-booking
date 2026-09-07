package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentUid(String paymentUid);
}
