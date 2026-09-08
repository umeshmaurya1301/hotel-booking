package com.umesh.hotelbooking.search;

import java.util.List;

/**
 * The escape hatch for a filter that cannot work one candidate at a time (design doc 10.1
 * task spec §5.2). {@code AvailabilityFilter} needs every surviving room type's id across
 * every surviving candidate <em>before</em> it queries {@code daily_inventory}, so it can
 * issue one batched query instead of one per candidate — with 40 candidate properties × 3 room
 * types, the per-candidate alternative is 120 queries for a single search.
 *
 * <p>{@link SearchFilterChain} checks {@code instanceof BatchSearchFilter} and routes
 * accordingly, rather than {@code SearchFilterChain} naming {@code AvailabilityFilter}
 * specifically — the latter would mean the chain has to change every time a filter's batching
 * needs change, exactly the coupling design doc 10.1's "zero changes elsewhere" rules out. Any
 * future filter that needs set-at-a-time access gets the same escape hatch for free by
 * implementing this interface instead of the plain one; the chain itself never changes either
 * way.
 *
 * <p>A {@code BatchSearchFilter}'s {@link SearchFilter#matches} is never called by the chain
 * and should be a documented no-op returning {@code true} — the real work happens in {@link
 * #applyBatch}, which mutates {@code candidates} (and the {@code roomTypes} lists inside them)
 * in place. The chain removes any candidate left with an empty room-type list after this runs,
 * the same cleanup step it applies after every other filter.
 */
public interface BatchSearchFilter extends SearchFilter {

    void applyBatch(List<SearchCandidate> candidates, SearchCriteria criteria);
}
