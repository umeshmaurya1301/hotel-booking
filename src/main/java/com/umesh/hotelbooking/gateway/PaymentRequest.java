package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * What we ask a provider to do. {@code providerReference} is generated once by
 * {@code PaymentService} and reused on every retry (design doc 8b) — the provider treats it
 * as the transaction's identity, so calling {@code initiate} twice with the same reference is
 * safe.
 */
public record PaymentRequest(
        String providerReference,
        PaymentMethod method,
        String bankCode,
        BigDecimal amount,
        String currency,
        SimulatedOutcome simulate) {
}
