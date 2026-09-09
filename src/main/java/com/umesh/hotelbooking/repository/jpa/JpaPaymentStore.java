package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.repository.PaymentStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link PaymentStore}. */
class JpaPaymentStore implements PaymentStore {

    private final JpaPaymentRepository repository;

    JpaPaymentStore(JpaPaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public Payment save(Payment payment) {
        return repository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Payment> findByPaymentUid(String paymentUid) {
        return repository.findByPaymentUid(paymentUid);
    }

    @Override
    public List<Payment> findByBookingId(Long bookingId) {
        return repository.findByBookingId(bookingId);
    }

    @Override
    public Optional<Payment> findByProviderReference(String providerReference) {
        return repository.findByProviderReference(providerReference);
    }

    @Override
    public List<Payment> findByStateAndNextAttemptAtBefore(PaymentState state, Instant cutoff) {
        return repository.findByStateAndNextAttemptAtBefore(state, cutoff);
    }

    @Override
    public List<Payment> findByStateAndInventoryReleasedFalse(PaymentState state) {
        return repository.findByStateAndInventoryReleasedFalse(state);
    }

    @Override
    public List<Payment> findByStateIn(List<PaymentState> states) {
        return repository.findByStateIn(states);
    }
}
