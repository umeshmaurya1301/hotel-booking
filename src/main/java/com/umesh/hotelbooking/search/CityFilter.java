package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

/**
 * The mandatory leading filter (design doc 10.1, task spec §6.1).
 *
 * <p>{@code PropertyStore.findForSearchByCityNormalised} already pre-filters by the same
 * predicate, so in the current implementation this filter will always pass — it is kept
 * anyway. The chain is the declared contract for how matching works; the day a geo-radius or
 * multi-city fetch replaces the single-city query, a chain with no city predicate of its own
 * would silently start returning properties from the wrong city. The cost is one string
 * comparison per candidate.
 */
@Component
public class CityFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return candidate.property().getCityNormalised().equals(criteria.cityNormalised());
    }

    @Override
    public int order() {
        return 10;
    }
}
