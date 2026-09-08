package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

/** An empty {@code criteria.amenities()} matches everything — {@code Set.containsAll} of an
 * empty set is vacuously true, so no separate "unset" branch is needed here either. */
@Component
public class AmenityFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return candidate.property().getAmenities().containsAll(criteria.amenities());
    }

    @Override
    public int order() {
        return 40;
    }
}
