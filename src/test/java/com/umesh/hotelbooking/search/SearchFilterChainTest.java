package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercised entirely with fake filters — no real filter, no database. This is deliberate: the
 * chain's contract (order, dropping, batch routing) must hold regardless of which filters are
 * plugged in, and testing it against the real ones would conflate "the chain works" with "this
 * one filter works".
 */
class SearchFilterChainTest {

    private static Property property(String uid) {
        RoomType roomType = RoomType.builder()
                .roomTypeUid(uid + "-rt")
                .name("Test Room")
                .totalUnits(2)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal("100.00"))
                .build();
        Property property = Property.builder()
                .propertyUid(uid)
                .name(uid)
                .city("City")
                .cityNormalised("city")
                .starRating(3)
                .zoneId("Asia/Kolkata")
                .currency("INR")
                .build();
        property.addRoomType(roomType);
        return property;
    }

    private static SearchCriteria criteria() {
        LocalDate day = LocalDate.of(2026, 10, 10);
        return SearchCriteria.of(new SearchPropertiesRequest(
                "City", null, day, day.plusDays(1), 1, 1, 0, null, null, null, null));
    }

    private static SearchFilter recording(int order, List<String> callOrder, String name) {
        return new SearchFilter() {
            @Override
            public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
                callOrder.add(name);
                return true;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    private static SearchFilter rejecting(int order) {
        return new SearchFilter() {
            @Override
            public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
                return false;
            }

            @Override
            public int order() {
                return order;
            }
        };
    }

    @Test
    void filtersRunInAscendingOrderRegardlessOfInjectionOrder() {
        List<String> callOrder = new ArrayList<>();
        // Deliberately supplied out of order (high before low) - the chain must sort, not trust the list.
        SearchFilterChain chain = new SearchFilterChain(List.of(
                recording(50, callOrder, "fifty"),
                recording(10, callOrder, "ten"),
                recording(30, callOrder, "thirty")));

        chain.apply(new ArrayList<>(List.of(SearchCandidate.of(property("p1")))), criteria());

        assertThat(callOrder).containsExactly("ten", "thirty", "fifty");
    }

    @Test
    void aShuffledInputListStillRunsCheapestFirst() {
        List<String> callOrder = new ArrayList<>();
        List<SearchFilter> shuffled = new ArrayList<>(List.of(
                recording(40, callOrder, "forty"),
                recording(20, callOrder, "twenty"),
                recording(10, callOrder, "ten"),
                recording(30, callOrder, "thirty")));
        java.util.Collections.shuffle(shuffled);

        SearchFilterChain chain = new SearchFilterChain(shuffled);
        chain.apply(new ArrayList<>(List.of(SearchCandidate.of(property("p1")))), criteria());

        assertThat(callOrder).containsExactly("ten", "twenty", "thirty", "forty");
    }

    @Test
    void aRejectingFilterDropsTheCandidate() {
        SearchFilterChain chain = new SearchFilterChain(List.of(rejecting(10)));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(SearchCandidate.of(property("p1"))));

        chain.apply(candidates, criteria());

        assertThat(candidates).isEmpty();
    }

    /** design doc 10.1 / task spec §0.1's own requirement, asserted directly: a filter added
     * to the injected list participates with no change to SearchFilterChain itself. */
    @Test
    void aNewFilterAddedToTheInjectedListParticipatesWithNoChainChange() {
        List<String> callOrder = new ArrayList<>();
        SearchFilter existing = recording(10, callOrder, "existing");
        SearchFilter newlyAdded = recording(20, callOrder, "new-filter");

        SearchFilterChain chain = new SearchFilterChain(List.of(existing, newlyAdded));
        chain.apply(new ArrayList<>(List.of(SearchCandidate.of(property("p1")))), criteria());

        assertThat(callOrder).containsExactly("existing", "new-filter");
    }

    @Test
    void aBatchFiltersMatchesIsNeverCalledByTheChain() {
        List<String> matchesCalls = new ArrayList<>();
        List<String> batchCalls = new ArrayList<>();
        BatchSearchFilter batch = new BatchSearchFilter() {
            @Override
            public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
                matchesCalls.add("matches");
                return true;
            }

            @Override
            public int order() {
                return 70;
            }

            @Override
            public void applyBatch(List<SearchCandidate> candidates, SearchCriteria criteria) {
                batchCalls.add("applyBatch");
            }
        };

        SearchFilterChain chain = new SearchFilterChain(List.of(batch));
        chain.apply(new ArrayList<>(List.of(SearchCandidate.of(property("p1")))), criteria());

        assertThat(batchCalls).containsExactly("applyBatch");
        assertThat(matchesCalls).isEmpty();
    }

    @Test
    void aBatchFilterEmptyingARoomTypeListDropsTheCandidate() {
        BatchSearchFilter emptiesEverything = new BatchSearchFilter() {
            @Override
            public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
                return true;
            }

            @Override
            public int order() {
                return 70;
            }

            @Override
            public void applyBatch(List<SearchCandidate> candidates, SearchCriteria criteria) {
                candidates.forEach(candidate -> candidate.retainRoomTypes(rt -> false));
            }
        };

        SearchFilterChain chain = new SearchFilterChain(List.of(emptiesEverything));
        List<SearchCandidate> candidates = new ArrayList<>(List.of(SearchCandidate.of(property("p1"))));

        chain.apply(candidates, criteria());

        assertThat(candidates).isEmpty();
    }

    @Test
    void anEmptyCandidateListShortCircuitsWithoutCallingLaterFilters() {
        List<String> callOrder = new ArrayList<>();
        SearchFilterChain chain = new SearchFilterChain(List.of(
                rejecting(10),
                recording(20, callOrder, "should-not-run")));

        chain.apply(new ArrayList<>(List.of(SearchCandidate.of(property("p1")))), criteria());

        assertThat(callOrder).isEmpty();
    }
}
