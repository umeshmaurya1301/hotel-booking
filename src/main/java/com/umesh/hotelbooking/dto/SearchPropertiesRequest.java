package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * The Phase 8 contract for {@code POST /api/v1/user/properties/search} (design doc 10.1) — a
 * placeholder only. No controller reads this yet; creating one that 500s would be worse than
 * leaving the endpoint absent (design doc §9.2 of this phase's task spec). Field shape follows
 * the filter chain design doc 10.1 lists: {@code CityFilter}, {@code LocalityFilter}, {@code
 * PriceRangeFilter}, {@code AmenityFilter}, {@code StarRatingFilter}, {@code
 * GuestCapacityFilter} and {@code AvailabilityFilter}, applied cheapest-first with the
 * inventory-touching availability check last.
 *
 * @param city required by the filter chain's mandatory leading filter
 * @param locality optional, narrows within the city
 * @param checkIn inclusive; together with {@code checkOut} drives {@code AvailabilityFilter}
 * @param checkOut exclusive, matching every other date range in this codebase
 * @param units rooms requested; feeds both availability and guest-capacity checks
 * @param adults at least one
 * @param children may be zero
 * @param minPricePerStay compared against the stay total, not a nightly rate (design doc 10.1)
 * @param maxPricePerStay compared against the stay total, not a nightly rate
 * @param minStarRating inclusive lower bound
 * @param amenities every requested amenity must be present on the property
 */
public record SearchPropertiesRequest(
        String city,
        String locality,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer units,
        Integer adults,
        Integer children,
        BigDecimal minPricePerStay,
        BigDecimal maxPricePerStay,
        Integer minStarRating,
        Set<Amenity> amenities) {
}
