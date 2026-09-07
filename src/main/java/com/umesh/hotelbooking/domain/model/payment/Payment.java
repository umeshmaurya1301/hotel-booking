package com.umesh.hotelbooking.domain.model.payment;

import com.umesh.hotelbooking.domain.vo.PaymentId;

/**
 * Placeholder for the Payment aggregate. Phase 1 needs only enough of this type to give
 * {@code PaymentRepository} a concrete signature; payment state, method and the gateway SPI
 * are built in the payment phase.
 */
public final class Payment {

    private final PaymentId id;

    public Payment(PaymentId id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        this.id = id;
    }

    public PaymentId getId() {
        return id;
    }
}
