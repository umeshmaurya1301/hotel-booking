package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Compares the <b>stay total</b>, not a nightly rate (design doc 10.1): with per-night pricing
 * (4.2.1), a nightly comparison would give inconsistent answers across a weekend boundary,
 * where one night's rate alone tells a guest nothing about what the whole stay costs.
 *
 * <p><b>Ordered at 80, after {@code AvailabilityFilter} (order 70) — not the 60 the original
 * design doc table implied — and reads {@code stayTotal} that filter already computed,
 * instead of loading {@code daily_inventory} a second time (task spec §6.2).</b> Both filters
 * need the same rows; loading them twice, or loading them here before {@code
 * AvailabilityFilter} has discarded the room types that turn out to be unavailable anyway,
 * is wasted work for no benefit. This is an implicit ordering dependency between two
 * components — {@code AvailabilityFilter} must run first, or every {@code stayTotal} here is
 * {@code null} — accepted deliberately to keep a single inventory fetch, and written down
 * here (and in {@code AvailabilityFilter}'s own Javadoc) precisely because an undocumented
 * ordering dependency between two otherwise-independent {@code @Component}s is exactly the
 * kind of thing that gets silently broken by a well-intentioned reordering later.
 *
 * <p>A {@code null} {@code stayTotal} — meaning this filter somehow ran before {@code
 * AvailabilityFilter} populated it — is treated as "does not match" rather than throwing: a
 * search endpoint failing an entire multi-property request because one filter's ordering
 * assumption was violated would be a worse failure mode than silently under-returning.
 */
@Component
public class PriceRangeFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return candidate.retainRoomTypes(roomTypeCandidate -> withinRange(roomTypeCandidate.stayTotal(), criteria));
    }

    private boolean withinRange(BigDecimal stayTotal, SearchCriteria criteria) {
        if (stayTotal == null) {
            return false;
        }
        if (criteria.minPricePerStay() != null && stayTotal.compareTo(criteria.minPricePerStay()) < 0) {
            return false;
        }
        return criteria.maxPricePerStay() == null || stayTotal.compareTo(criteria.maxPricePerStay()) <= 0;
    }

    @Override
    public int order() {
        return 80;
    }
}
