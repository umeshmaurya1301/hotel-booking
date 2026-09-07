package com.umesh.hotelbooking.domain.vo;

/**
 * Number of guests on a booking. A booking with zero adults is invalid: a room cannot be
 * booked for children unaccompanied by at least one adult.
 */
public record GuestCount(int adults, int children) {

    public GuestCount {
        if (adults < 1) {
            throw new IllegalArgumentException("adults must be at least 1, got: " + adults);
        }
        if (children < 0) {
            throw new IllegalArgumentException("children must not be negative, got: " + children);
        }
    }

    public int total() {
        return adults + children;
    }
}
