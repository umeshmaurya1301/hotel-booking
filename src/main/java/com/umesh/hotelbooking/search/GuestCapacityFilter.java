package com.umesh.hotelbooking.search;

import org.springframework.stereotype.Component;

/**
 * Room-type granularity, not property (design doc 10.1): {@code units × roomType.maxGuests >=
 * totalGuests}, checked per room type. A property keeps only its sufficiently-large room
 * types — it is not dropped whole just because one of its room types cannot fit the party.
 */
@Component
public class GuestCapacityFilter implements SearchFilter {

    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return candidate.retainRoomTypes(rt ->
                rt.roomType().getMaxGuests() * criteria.units() >= criteria.totalGuests());
    }

    @Override
    public int order() {
        return 50;
    }
}
