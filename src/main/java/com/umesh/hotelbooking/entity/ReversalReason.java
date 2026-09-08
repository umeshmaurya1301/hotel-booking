package com.umesh.hotelbooking.entity;

/**
 * Why a settled payment is being undone rather than the booking simply standing (design doc
 * 9.2). A reversal is always full and never policy-applied — that is what distinguishes it
 * from a refund (9.1).
 */
public enum ReversalReason {
    /**
     * Paid, but the reservation could not complete. Currently unreachable by construction:
     * this system reserves inventory at booking-creation time, before any payment is
     * attempted, so a reservation failure is always known before a charge is ever initiated.
     * The value is kept for completeness and for a future flow where that ordering differs.
     */
    RESERVATION_FAILED_AFTER_PAYMENT,

    /** Breaker was open or the gateway timed out; the hold lapsed; success arrived late. */
    LATE_SUCCESS_ON_EXPIRED_BOOKING,

    DUPLICATE_CHARGE,

    MANUAL_CORRECTION
}
