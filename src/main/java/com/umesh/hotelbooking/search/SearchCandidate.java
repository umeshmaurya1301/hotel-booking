package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.entity.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A property still in the running, together with the subset of its room types that have not
 * yet been eliminated. This is the locked resolution to design doc 10.1's own interface not
 * being implementable as written — see {@link SearchFilter}'s Javadoc for the full reasoning.
 *
 * <p>Property-level filters ({@code CityFilter}, {@code LocalityFilter}, {@code
 * StarRatingFilter}, {@code AmenityFilter}) ignore {@link #roomTypes()} and answer for the
 * candidate as a whole. Room-type-level filters ({@code GuestCapacityFilter}, {@code
 * PriceRangeFilter}, {@code AvailabilityFilter}) call {@link #retainRoomTypes} to prune the
 * list in place; a candidate whose room-type list empties is dropped by {@link
 * SearchFilterChain} on the next pass.
 *
 * <p><b>Mutable and created fresh per search request.</b> This is a per-request scratch
 * object, not a domain type — never cache, share, or store one across requests.
 */
public final class SearchCandidate {

    private final Property property;
    private final List<RoomTypeCandidate> roomTypes;

    private SearchCandidate(Property property, List<RoomTypeCandidate> roomTypes) {
        this.property = property;
        this.roomTypes = roomTypes;
    }

    /** Must be called inside the transaction that loaded {@code property} — {@code
     * roomTypes} must already be an initialised (eagerly fetched) collection. */
    public static SearchCandidate of(Property property) {
        List<RoomTypeCandidate> roomTypes = new ArrayList<>(property.getRoomTypes().size());
        for (var roomType : property.getRoomTypes()) {
            roomTypes.add(new RoomTypeCandidate(roomType));
        }
        return new SearchCandidate(property, roomTypes);
    }

    public Property property() {
        return property;
    }

    public List<RoomTypeCandidate> roomTypes() {
        return roomTypes;
    }

    /**
     * Prunes {@link #roomTypes()} in place to only those satisfying {@code keep}.
     *
     * @return {@code false} if every room type was pruned — the caller (a room-type-level
     *     filter's {@code matches()}) returns this directly, so the chain drops a candidate
     *     with nothing left exactly the way it would drop one a property-level filter rejected.
     */
    public boolean retainRoomTypes(Predicate<RoomTypeCandidate> keep) {
        roomTypes.removeIf(keep.negate());
        return !roomTypes.isEmpty();
    }
}
