package com.umesh.hotelbooking.domain.vo;

import java.math.BigDecimal;

/**
 * A property's geographic location. {@code locality} is optional. {@code latitude} and
 * {@code longitude} are optional but must both be present or both absent, and within valid
 * ranges when present.
 */
public record Location(String city, String locality, BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LAT = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LAT = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LNG = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LNG = BigDecimal.valueOf(180);

    public Location {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("city must not be null or blank");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException(
                    "latitude and longitude must both be present or both absent");
        }
        if (latitude != null) {
            if (latitude.compareTo(MIN_LAT) < 0 || latitude.compareTo(MAX_LAT) > 0) {
                throw new IllegalArgumentException("latitude out of range: " + latitude);
            }
            if (longitude.compareTo(MIN_LNG) < 0 || longitude.compareTo(MAX_LNG) > 0) {
                throw new IllegalArgumentException("longitude out of range: " + longitude);
            }
        }
    }

    /**
     * Trimmed, lowercased, internal-whitespace-collapsed city name used for search matching so
     * that "Bengaluru", "bengaluru" and " Bengaluru " match the same city. Original casing is
     * preserved in {@link #city()} for display.
     */
    public String normalisedCity() {
        return city.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
