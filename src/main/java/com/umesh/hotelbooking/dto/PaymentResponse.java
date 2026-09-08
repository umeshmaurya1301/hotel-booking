package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code state} is deliberately visible as-is rather than collapsed to success/failure: an
 * API that can only say yes or no cannot express {@code UNKNOWN}, and pretending otherwise is
 * exactly the guess design doc 7.2 refuses to make.
 */
public record PaymentResponse(
        String paymentUid,
        String bookingUid,
        PaymentMethod method,
        String providerCode,
        BigDecimal amount,
        String currency,
        PaymentState state,
        int attemptNo,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment, String bookingUid) {
        return new PaymentResponse(
                payment.getPaymentUid(), bookingUid, payment.getMethod(), payment.getProviderCode(),
                payment.getAmount(), payment.getCurrency(), payment.getState(), payment.getAttemptNo(),
                payment.getNextAttemptAt(), payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
