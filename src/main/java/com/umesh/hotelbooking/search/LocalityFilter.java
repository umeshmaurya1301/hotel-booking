package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

/** Skipped entirely when {@code criteria.locality()} is null — narrowing within a city is
 * optional. When supplied, a case-insensitive equality check against {@code
 * property.getLocality()}, which may itself be null (locality is optional on onboarding). */
@Component
public class LocalityFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        if (criteria.locality() == null) {
            return true;
        }
        String propertyLocality = candidate.property().getLocality();
        return propertyLocality != null && propertyLocality.equalsIgnoreCase(criteria.locality());
    }

    @Override
    public int order() {
        return 20;
    }
}
