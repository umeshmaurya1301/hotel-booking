package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.exception.InvalidDateRangeException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The validated, normalised form every {@link SearchFilter} sees. {@link
 * SearchPropertiesRequest} is the wire DTO — nullable boxed fields, whatever the client sent;
 * this is what a filter may assume is already correct, so no filter re-validates or
 * re-derives anything from the raw request.
 *
 * <p>Three things are computed exactly once here rather than per filter: the expanded {@link
 * #nights()} list, {@link #totalGuests()}, and {@link #cityNormalised()}. Seven filters each
 * re-deriving the night list from two dates is the kind of duplication that is invisible
 * until a range is off by one in exactly one of them.
 *
 * @param cityNormalised produced by {@link Property#normalise(String)} — the same normaliser
 *     the entity uses to populate the indexed column a search matches against. Calling that
 *     one static helper, rather than writing a second normaliser here, is what keeps "Bengaluru
 *     " and "bengaluru" matching each other for as long as both call sites exist.
 * @param checkIn inclusive
 * @param checkOut exclusive, matching every other date range in this codebase
 * @param nights {@code [checkIn, checkOut)}, expanded once
 * @param totalGuests {@code adults + children}, summed once
 * @param minStarRating 0 when unspecified — every property has a star rating of at least 1,
 *     so 0 is a safe "no floor" sentinel with no reserved value collision
 * @param amenities empty, never {@code null}, when unspecified
 */
public record SearchCriteria(
        String cityNormalised,
        String locality,
        LocalDate checkIn,
        LocalDate checkOut,
        List<LocalDate> nights,
        int units,
        int totalGuests,
        BigDecimal minPricePerStay,
        BigDecimal maxPricePerStay,
        int minStarRating,
        Set<Amenity> amenities) {

    public static SearchCriteria of(SearchPropertiesRequest request) {
        if (request.checkIn() == null || request.checkOut() == null) {
            throw new InvalidDateRangeException("checkIn and checkOut must not be null");
        }
        if (!request.checkOut().isAfter(request.checkIn())) {
            throw new InvalidDateRangeException(
                    "checkOut (" + request.checkOut() + ") must be strictly after checkIn (" + request.checkIn() + ")");
        }
        List<LocalDate> nights = request.checkIn().datesUntil(request.checkOut()).toList();
        int children = request.children() == null ? 0 : request.children();
        int totalGuests = request.adults() + children;

        return new SearchCriteria(
                Property.normalise(request.city()),
                request.locality(),
                request.checkIn(),
                request.checkOut(),
                nights,
                request.units(),
                totalGuests,
                request.minPricePerStay(),
                request.maxPricePerStay(),
                request.minStarRating() == null ? 0 : request.minStarRating(),
                request.amenities() == null ? Set.of() : request.amenities());
    }
}
