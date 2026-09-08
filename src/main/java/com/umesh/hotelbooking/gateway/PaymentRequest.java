package com.umesh.hotelbooking.gateway;

import com.umesh.hotelbooking.entity.PaymentMethod;

import java.math.BigDecimal;

/**
 * What we ask a provider to do. {@code providerReference} is generated once by
 * {@code PaymentService} and reused on every retry (design doc 8b) — the provider treats it
 * as the transaction's identity, so calling {@code initiate} twice with the same reference is
 * safe.
 *
 * <p>The instrument fields below are optional and method-shaped: {@code cardNumber}/{@code
 * cvv}/{@code billingName} for CARD, {@code vpa} for UPI, {@code walletId}/{@code
 * contactPhone} for WALLET (design doc 12.6.6). Nothing in this codebase collects real
 * instrument data from a guest, so every current caller uses the six-argument constructor
 * below and leaves them null; the mock providers synthesize demonstration-only, obviously
 * fake values (e.g. {@code 4111 1111 1111 1111}) when they are absent, purely so the
 * redaction path in {@code PayloadRedactor} has PAN-, CVV- and contact-shaped data to
 * actually redact rather than merely being described (12.6.6). {@code cvv} is accepted here
 * and nowhere else — it is never a field on any persisted entity (design doc 12.6.2).
 */
public record PaymentRequest(
        String providerReference,
        PaymentMethod method,
        String bankCode,
        BigDecimal amount,
        String currency,
        SimulatedOutcome simulate,
        String cardNumber,
        String cvv,
        String billingName,
        String vpa,
        String walletId,
        String contactPhone) {

    public PaymentRequest(String providerReference, PaymentMethod method, String bankCode,
                          BigDecimal amount, String currency, SimulatedOutcome simulate) {
        this(providerReference, method, bankCode, amount, currency, simulate,
                null, null, null, null, null, null);
    }
}
