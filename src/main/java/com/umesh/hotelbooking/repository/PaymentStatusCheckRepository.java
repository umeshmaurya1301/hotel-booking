package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Append-only: no update or delete beyond what {@code JpaRepository} exposes. */
public interface PaymentStatusCheckRepository extends JpaRepository<PaymentStatusCheck, Long> {

    List<PaymentStatusCheck> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
}
