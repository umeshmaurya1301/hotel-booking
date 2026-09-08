package com.umesh.hotelbooking.search;

/**
 * A pluggable predicate over one {@link SearchCandidate} (design doc 10.1). Registered as a
 * {@code @Component}; adding a new one is one new class and zero changes anywhere else — the
 * brief's explicit requirement (design doc 10.1's own words: "Adding a filter is one new
 * {@code @Component} and zero changes elsewhere").
 *
 * <p><b>Deviation from design doc 10.1's literal signature — read before adding a filter.</b>
 * The design document specifies {@code boolean matches(Property property, SearchCriteria
 * criteria)}. That signature cannot express three of its own seven listed filters:
 * {@code GuestCapacityFilter} needs {@code roomType.maxGuests}, {@code PriceRangeFilter} needs
 * the per-night price of a specific room type, and {@code AvailabilityFilter} needs
 * booked/total units per room type per night — none of which a bare {@code Property} carries,
 * because a property does not have <em>one</em> capacity, price or availability, it has one
 * per room type. A {@code boolean} result also cannot say <em>which</em> room type matched,
 * leaving a guest unable to identify what to actually book.
 *
 * <p>The signature here operates on {@link SearchCandidate} instead: the property plus its
 * still-alive room types, pruned in place as filters run. This keeps the brief's constraint
 * intact letter and spirit — an eighth filter is still one new {@code @Component} implementing
 * only this interface (or {@link BatchSearchFilter}) — while making every survivor's matching
 * room types, prices and availability part of the result rather than lost information.
 *
 * @see BatchSearchFilter for the one filter kind that needs every surviving candidate at once
 *     rather than one at a time
 */
public interface SearchFilter {

    /**
     * @return {@code true} if {@code candidate} survives this filter. A property-level filter
     *     answers for the candidate as a whole; a room-type-level filter prunes {@link
     *     SearchCandidate#roomTypes()} via {@link SearchCandidate#retainRoomTypes} and returns
     *     that call's result directly.
     */
    boolean matches(SearchCandidate candidate, SearchCriteria criteria);

    /** Lower runs first. Spaced by 10 so a future filter can slot between two without
     * renumbering every filter after it (design doc 10.2: cheap predicates before inventory
     * reads). */
    int order();
}
