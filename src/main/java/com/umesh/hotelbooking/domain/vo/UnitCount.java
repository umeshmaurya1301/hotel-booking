package com.umesh.hotelbooking.domain.vo;

/**
 * Number of physical rooms requested in a booking. Kept distinct from {@link GuestCount} so
 * that "2 rooms" and "2 guests" cannot be confused by a swapped constructor argument.
 */
public record UnitCount(int value) {

    /**
     * Maximum rooms a single booking may request. A domain rule stated here rather than read
     * from configuration, since the domain has no config access.
     */
    public static final int MAX_UNITS_PER_BOOKING = 10;

    public UnitCount {
        if (value < 1) {
            throw new IllegalArgumentException("value must be at least 1, got: " + value);
        }
        if (value > MAX_UNITS_PER_BOOKING) {
            throw new IllegalArgumentException(
                    "value must not exceed " + MAX_UNITS_PER_BOOKING + " per booking, got: " + value);
        }
    }

    public static UnitCount of(int value) {
        return new UnitCount(value);
    }

    public static UnitCount one() {
        return new UnitCount(1);
    }
}
