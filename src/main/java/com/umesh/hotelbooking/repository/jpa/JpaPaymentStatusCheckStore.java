package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import com.umesh.hotelbooking.repository.PaymentStatusCheckStore;

import java.util.List;

/** JPA adapter for {@link PaymentStatusCheckStore}. */
class JpaPaymentStatusCheckStore implements PaymentStatusCheckStore {

    private final JpaPaymentStatusCheckRepository repository;

    JpaPaymentStatusCheckStore(JpaPaymentStatusCheckRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaymentStatusCheck save(PaymentStatusCheck statusCheck) {
        return repository.save(statusCheck);
    }

    @Override
    public List<PaymentStatusCheck> findByPaymentIdOrderByAttemptNoAsc(Long paymentId) {
        return repository.findByPaymentIdOrderByAttemptNoAsc(paymentId);
    }
}
