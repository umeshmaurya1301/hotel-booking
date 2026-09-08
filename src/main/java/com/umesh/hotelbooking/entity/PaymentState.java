package com.umesh.hotelbooking.entity;

/**
 * Lifecycle of one payment attempt (design doc 3.5), independent of {@link BookingState}.
 * A booking's cancellation status and a payment's settlement status are different facts the
 * business needs separately — collapsing them is a common modelling error.
 *
 * <p>Note what is <em>not</em> here: there is no {@code REVERSED}. A reversal means the
 * booking could not stand even though the money moved, so the payment itself stays
 * {@code SETTLED} — reversal is recorded against the booking and, in a later phase, the
 * ledger, not by inventing a payment state that would contradict "the charge succeeded".
 */
public enum PaymentState {
    INITIATED,
    PROCESSING,
    SETTLED,
    FAILED,
    UNKNOWN,
    MANUAL_REVIEW
}
