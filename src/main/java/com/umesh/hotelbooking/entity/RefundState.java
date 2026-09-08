package com.umesh.hotelbooking.entity;

/** Lifecycle of a {@link Refund} (design doc 3.5), independent of {@link BookingState}. */
public enum RefundState {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED
}
