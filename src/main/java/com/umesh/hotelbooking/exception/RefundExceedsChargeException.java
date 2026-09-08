package com.umesh.hotelbooking.exception;

import java.math.BigDecimal;

/**
 * Thrown when a refund or reversal would push {@code sum(REFUND) + sum(REVERSAL)} for a
 * booking past {@code sum(CHARGE)} — the ledger invariant of design doc 9.4, checked before
 * the entry is ever written rather than caught after the fact.
 */
public final class RefundExceedsChargeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RefundExceedsChargeException(String bookingUid, BigDecimal attempted, BigDecimal remaining) {
        super("REFUND_EXCEEDS_CHARGE",
                "Refund/reversal of " + attempted + " for booking " + bookingUid
                        + " exceeds the " + remaining + " remaining against its charges");
    }
}
