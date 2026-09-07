package com.umesh.hotelbooking.domain.model.booking;

/**
 * Lifecycle states of a {@link Booking}. CANCELLED, COMPLETED, EXPIRED and REVERSED are
 * terminal: {@link BookingStateMachine} permits no transition out of them except to
 * themselves.
 */
public enum BookingState {
    CREATED,
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    EXPIRED,
    PAYMENT_FAILED,
    PAYMENT_UNKNOWN,
    MANUAL_REVIEW,
    REVERSED
}
