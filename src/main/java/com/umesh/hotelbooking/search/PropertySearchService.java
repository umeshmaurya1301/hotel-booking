package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.config.SearchProperties;
import com.umesh.hotelbooking.dto.PropertySearchResult;
import com.umesh.hotelbooking.dto.RoomTypeSearchResult;
import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.dto.SearchResponse;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.repository.PropertyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Guest-facing discovery (design doc 10). Read-only and advisory (10.3) — no write, no lock,
 * no reservation happens on this path; a booking's own atomic reservation (5.2) is what is
 * actually authoritative.
 */
@Service
public class PropertySearchService {

    private static final Logger log = LoggerFactory.getLogger(PropertySearchService.class);

    private final PropertyRepository propertyRepository;
    private final SearchFilterChain filterChain;
    private final SearchProperties searchProperties;

    public PropertySearchService(PropertyRepository propertyRepository, SearchFilterChain filterChain,
                                 SearchProperties searchProperties) {
        this.propertyRepository = propertyRepository;
        this.filterChain = filterChain;
        this.searchProperties = searchProperties;
    }

    /**
     * {@code readOnly = true} is not decoration here: it tells Hibernate to skip
     * dirty-checking on what will be the largest object graph the application loads — every
     * candidate property together with its room types and amenities.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(SearchPropertiesRequest request) {
        SearchCriteria criteria = SearchCriteria.of(request);

        List<SearchCandidate> candidates = new ArrayList<>();
        for (Property property : propertyRepository.findForSearchByCityNormalised(criteria.cityNormalised())) {
            candidates.add(SearchCandidate.of(property));
        }
        int candidateCount = candidates.size();

        filterChain.apply(candidates, criteria);

        SearchResponse response = toResponse(candidates);

        // Not the full criteria object - when a search returns nothing, the answer is almost
        // always in the drop-off between these two counts, and this line is what makes the
        // filter chain debuggable without a debugger.
        log.debug("search city={} nights={} units={} candidatesIn={} resultsOut={}",
                criteria.cityNormalised(), criteria.nights().size(), criteria.units(), candidateCount, response.resultCount());

        return response;
    }

    private SearchResponse toResponse(List<SearchCandidate> candidates) {
        List<PropertySearchResult> sorted = candidates.stream()
                .map(this::toResult)
                // Cheapest matching room type ascending, then star rating descending, then
                // propertyUid ascending as a stable tiebreak - without the tiebreak, two
                // properties with identical price and rating would swap order between
                // requests, which looks like a bug to anyone paging through results by eye.
                .sorted(Comparator.comparing(PropertySearchService::cheapestStayTotal)
                        .thenComparing(PropertySearchResult::starRating, Comparator.reverseOrder())
                        .thenComparing(PropertySearchResult::propertyUid))
                .toList();

        boolean truncated = sorted.size() > searchProperties.maxResults();
        List<PropertySearchResult> capped = truncated ? sorted.subList(0, searchProperties.maxResults()) : sorted;

        return new SearchResponse(capped.size(), truncated, capped);
    }

    private PropertySearchResult toResult(SearchCandidate candidate) {
        Property property = candidate.property();
        List<RoomTypeSearchResult> roomTypes = candidate.roomTypes().stream()
                .map(rt -> new RoomTypeSearchResult(
                        rt.roomType().getRoomTypeUid(), rt.roomType().getName(), rt.roomType().getMaxGuests(),
                        rt.availableUnits(), rt.stayTotal(), property.getCurrency()))
                .toList();

        return new PropertySearchResult(
                property.getPropertyUid(), property.getName(), property.getCity(), property.getLocality(),
                property.getStarRating(), new LinkedHashSet<>(property.getAmenities()), roomTypes);
    }

    /** Sorting key only — every surviving result has at least one room type, since the filter
     * chain drops any candidate whose room-type list emptied, so this never actually falls
     * back to the defensive zero. */
    private static BigDecimal cheapestStayTotal(PropertySearchResult result) {
        return result.roomTypes().stream()
                .map(RoomTypeSearchResult::stayTotal)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }
}
