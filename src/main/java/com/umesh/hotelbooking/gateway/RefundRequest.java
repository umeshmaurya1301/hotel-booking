package com.umesh.hotelbooking.gateway;

import java.math.BigDecimal;

/** Contract settled now, exercised starting in the cancellation/refund phase. */
public record RefundRequest(String providerReference, BigDecimal amount, String currency) {
}
