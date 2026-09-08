package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * The Phase 8 contract for {@code POST /api/v1/user/properties/search} (design doc 10.1).
 * Field shape follows the filter chain design doc 10.1 lists: {@code CityFilter}, {@code
 * LocalityFilter}, {@code PriceRangeFilter}, {@code AmenityFilter}, {@code StarRatingFilter},
 * {@code GuestCapacityFilter} and {@code AvailabilityFilter}, applied cheapest-first with the
 * inventory-touching availability check last.
 *
 * <p>{@code checkOut > checkIn} is a cross-field rule and deliberately not expressed here as a
 * Bean Validation annotation — it belongs in {@code SearchCriteria.of(...)}, thrown as an
 * {@code InvalidDateRangeException}, exactly the way {@code Booking.validateDateRange()}
 * handles the identical rule for {@code CreateBookingRequest}. Two different mechanisms for
 * the same rule on two sibling endpoints would be the inconsistency worth avoiding.
 *
 * @param city required by the filter chain's mandatory leading filter — see search doc §5.1:
 *     this is what lets the candidate fetch use {@code idx_property_city_rating} instead of a
 *     full table scan, not merely a UX preference.
 * @param locality optional, narrows within the city
 * @param checkIn inclusive; together with {@code checkOut} drives {@code AvailabilityFilter}
 * @param checkOut exclusive, matching every other date range in this codebase
 * @param units rooms requested; feeds both availability and guest-capacity checks. Capped at
 *     10, mirroring {@code CreateBookingRequest} exactly — the two endpoints disagreeing on
 *     this limit would let a guest find a result they cannot then book.
 * @param adults at least one
 * @param children may be zero; {@code null} is treated as zero
 * @param minPricePerStay compared against the stay total, not a nightly rate (design doc 10.1)
 * @param maxPricePerStay compared against the stay total, not a nightly rate
 * @param minStarRating inclusive lower bound; {@code null} is treated as unset (0)
 * @param amenities every requested amenity must be present on the property; {@code null} is
 *     treated as empty (no amenity constraint)
 */
public record SearchPropertiesRequest(
        @NotBlank @Size(max = 120) String city,
        @Size(max = 160) String locality,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @NotNull @Min(1) @Max(10) Integer units,
        @NotNull @Min(1) Integer adults,
        @Min(0) Integer children,
        @DecimalMin("0.00") BigDecimal minPricePerStay,
        @DecimalMin("0.00") BigDecimal maxPricePerStay,
        @Min(1) @Max(5) Integer minStarRating,
        Set<Amenity> amenities) {
}
