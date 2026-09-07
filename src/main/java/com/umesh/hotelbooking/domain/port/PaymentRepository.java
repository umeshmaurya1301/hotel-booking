package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.payment.Payment;
import com.umesh.hotelbooking.domain.vo.PaymentId;

import java.util.Optional;

/**
 * Persistence port for {@link Payment}. Minimal for Phase 1; the payment phase extends this
 * with lookups by state (for the reconciliation loop) and by provider reference.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);
}
