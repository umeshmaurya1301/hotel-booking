package com.umesh.hotelbooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Creates a booking and holds its room-nights (design doc 11, POST /api/v1/user/bookings).
 *
 * <p>Dates are property-local calendar dates, not instants: a hotel night is "the night of
 * the 14th" at that hotel regardless of where the guest is sitting. {@code checkOut} is
 * exclusive — a 10th-to-13th booking occupies the nights of the 10th, 11th and 12th.
 *
 * @param guestUid optional; a guest is created when absent, so a first-time booker does not
 *     need a separate registration call
 * @param units number of rooms. Capped per booking by {@code @Max}; a booking for 200 rooms
 *     is a group enquiry, not a self-service transaction.
 */
public record CreateBookingRequest(
        String guestUid,
        @NotBlank String roomTypeUid,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Min(1) @Max(10) int units,
        @Min(1) int adults,
        @Min(0) int children) {
}
