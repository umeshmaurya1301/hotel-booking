package com.umesh.hotelbooking.gateway;

import java.math.BigDecimal;

/** Contract settled now, exercised starting in the reversal phase (design doc 9.2). */
public record ReversalRequest(String providerReference, BigDecimal amount, String currency, String reason) {
}
