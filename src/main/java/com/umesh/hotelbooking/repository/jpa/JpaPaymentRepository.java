package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaPaymentStore}. Not injected outside this package. */
interface JpaPaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentUid(String paymentUid);

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByProviderReference(String providerReference);

    List<Payment> findByStateAndNextAttemptAtBefore(PaymentState state, Instant cutoff);

    List<Payment> findByStateAndInventoryReleasedFalse(PaymentState state);

    List<Payment> findByStateIn(List<PaymentState> states);
}
