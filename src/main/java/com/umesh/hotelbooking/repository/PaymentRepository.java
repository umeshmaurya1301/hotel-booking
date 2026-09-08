package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentUid(String paymentUid);

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByProviderReference(String providerReference);

    /** Due for the next rung of the status-check ladder (design doc 7.6.4). */
    List<Payment> findByStateAndNextAttemptAtBefore(PaymentState state, Instant cutoff);

    /** Past the hold window but not yet released — the T+15m step of 7.6.1, checked separately
     * from the ladder because it runs on its own clock. */
    List<Payment> findByStateAndInventoryReleasedFalse(PaymentState state);

    /** GET /api/v1/admin/payments/stuck: everything a human might need to look at. */
    List<Payment> findByStateIn(List<PaymentState> states);
}
