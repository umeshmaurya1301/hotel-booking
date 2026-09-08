package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Runs every {@link SearchFilter} bean in {@code order()} sequence (design doc 10.1, 10.2).
 * Never names a concrete filter — a new one is one {@code @Component} implementing {@link
 * SearchFilter} or {@link BatchSearchFilter} and nothing here changes, which is the whole
 * point of design doc 10.1's "one new {@code @Component}, zero changes elsewhere".
 *
 * <p>Ordering is design doc 10.2's performance argument made concrete: four zero-cost
 * in-memory property predicates ({@code CityFilter}, {@code LocalityFilter}, {@code
 * StarRatingFilter}, {@code AmenityFilter}) run first, then {@code GuestCapacityFilter} (still
 * free — no inventory read), then the two filters that touch {@code daily_inventory} last.
 * Filter ordering as a performance decision is, in design doc 10.2's own words, "a reasonable
 * thing to be asked about" — this is the answer.
 *
 * <p>A candidate is dropped the moment its room-type list empties, whether that happened
 * because a plain filter's {@link SearchFilter#matches} returned {@code false} or because a
 * {@link BatchSearchFilter#applyBatch} pruned every room type out of it — both are checked
 * after every filter runs, uniformly, so the chain does not need to know which kind of filter
 * just ran.
 */
@Component
public class SearchFilterChain {

    private final List<SearchFilter> filters;

    public SearchFilterChain(List<SearchFilter> filters) {
        // Sorted once, in the constructor, not per request — sorting an injected list on
        // every call would be a small waste repeated on the hottest path in the system.
        this.filters = filters.stream()
                .sorted(Comparator.comparingInt(SearchFilter::order))
                .toList();
    }

    public void apply(List<SearchCandidate> candidates, SearchCriteria criteria) {
        for (SearchFilter filter : filters) {
            if (candidates.isEmpty()) {
                break;
            }
            if (filter instanceof BatchSearchFilter batchFilter) {
                batchFilter.applyBatch(candidates, criteria);
            } else {
                candidates.removeIf(candidate -> !filter.matches(candidate, criteria));
            }
            candidates.removeIf(candidate -> candidate.roomTypes().isEmpty());
        }
    }
}
