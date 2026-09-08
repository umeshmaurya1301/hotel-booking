package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

/** {@code criteria.minStarRating() == 0} means unspecified (see {@link SearchCriteria}'s own
 * Javadoc) and every property has a rating of at least 1, so the inclusive {@code >=} check
 * matches everything when no floor was requested — no separate "unset" branch needed. */
@Component
public class StarRatingFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return candidate.property().getStarRating() >= criteria.minStarRating();
    }

    @Override
    public int order() {
        return 30;
    }
}
