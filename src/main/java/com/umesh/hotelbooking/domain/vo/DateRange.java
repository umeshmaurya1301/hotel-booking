package com.umesh.hotelbooking.domain.vo;

import com.umesh.hotelbooking.domain.exception.InvalidDateRangeException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * An inclusive-checkIn, exclusive-checkOut span of calendar nights.
 *
 * <p>Checkout-exclusive semantics are the single most important rule in this class: a hotel
 * night is the night a guest sleeps there, and checkout day is not one. A range from the 10th
 * to the 13th covers exactly the nights of the 10th, 11th and 12th. Getting this wrong
 * produces an off-by-one overbooking that no other test will catch.
 *
 * <p>{@link #nights()} is the only place in the codebase that expands a range into discrete
 * dates; both inventory materialisation and reservation depend on that being true.
 */
public record DateRange(LocalDate checkIn, LocalDate checkOut) {

    public static final int MAX_STAY_NIGHTS = 30;

    public DateRange {
        if (checkIn == null || checkOut == null) {
            throw new InvalidDateRangeException("checkIn and checkOut must not be null");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidDateRangeException(
                    "checkOut (" + checkOut + ") must be after checkIn (" + checkIn + ")");
        }
        long spanNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (spanNights > MAX_STAY_NIGHTS) {
            throw new InvalidDateRangeException(
                    "stay of " + spanNights + " nights exceeds the maximum of " + MAX_STAY_NIGHTS);
        }
    }

    /**
     * Every calendar date from {@code checkIn} inclusive to {@code checkOut} exclusive, in
     * ascending order. The sole place in the codebase a date range becomes discrete nights.
     */
    public List<LocalDate> nights() {
        List<LocalDate> result = new ArrayList<>(nightCount());
        for (LocalDate date = checkIn; date.isBefore(checkOut); date = date.plusDays(1)) {
            result.add(date);
        }
        return result;
    }

    /** Equal to {@code nights().size()}, computed directly rather than by building the list. */
    public int nightCount() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * Half-open interval overlap: a range ending on the day another begins does not overlap.
     * One guest checking out and another checking into the same room on the same day is a
     * legal same-day turnover, not a conflict.
     */
    public boolean overlaps(DateRange other) {
        return checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(checkOut);
    }

    /** True for {@code checkIn <= date < checkOut}. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(checkIn) && date.isBefore(checkOut);
    }

    /** True once the stay has fully checked out on or before {@code reference}. */
    public boolean isBefore(LocalDate reference) {
        return !checkOut.isAfter(reference);
    }
}
