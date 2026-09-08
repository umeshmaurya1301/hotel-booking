package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one filter that touches {@code daily_inventory} (design doc 10.1, 10.2) — last in the
 * chain, and a {@link BatchSearchFilter} rather than a plain one, so it can issue a single
 * batched query across every surviving room type instead of one query per candidate (task
 * spec §5.2).
 *
 * <p>Design doc's rule, verbatim: {@code bookedUnits + requestedUnits <= totalUnits}, not
 * {@code bookedUnits < totalUnits} — a search for 2 rooms must exclude a property with only 1
 * free. That is exactly {@link DailyInventory#hasCapacityFor(int)}, reused rather than
 * reimplemented. One insufficient night excludes the whole room type (all-or-nothing,
 * mirroring the reservation path's own atomicity, design doc 5.2.3).
 *
 * <p><b>Missing inventory is not availability (task spec §5.3).</b> A room type is excluded,
 * not errored, when the materialised horizon does not reach the requested range — search
 * excludes; it does not throw {@code InventoryNotMaterialisedException}, since one property's
 * short horizon must not fail an entire multi-property search.
 *
 * <p><b>Property-local dates (design doc 4.5, task spec §5.4).</b> A candidate is dropped
 * outright when the requested range starts before <em>today in that property's own zone</em>
 * — computed per property, inside this filter, not once for the whole search, since two
 * properties in one search can sit in different zones and "yesterday" is a property-local
 * fact.
 *
 * <p><b>Also sets {@code stayTotal} (task spec §6.2).</b> {@code PriceRangeFilter} needs the
 * same {@code daily_inventory} rows this filter already loaded; loading them twice — or
 * loading them in a filter that runs before availability has discarded the room types that
 * do not need pricing at all — would be wasteful. Rather than sharing a fetch through a
 * separate collaborator, {@code PriceRangeFilter} was moved to order 80 (after this one) and
 * simply reads the {@code stayTotal} this filter already computed. That is an implicit
 * ordering dependency between two components, accepted deliberately; see {@code
 * PriceRangeFilter}'s own Javadoc for the other half of this note.
 */
@Component
public class AvailabilityFilter implements BatchSearchFilter {

    private final DailyInventoryRepository dailyInventoryRepository;
    private final Clock clock;

    public AvailabilityFilter(DailyInventoryRepository dailyInventoryRepository, Clock clock) {
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.clock = clock;
    }

    /** Never called by {@link SearchFilterChain} — see {@link BatchSearchFilter}'s Javadoc.
     * Returns {@code true} unconditionally so a direct call (defensive interface completeness)
     * never silently drops a candidate this filter has not actually evaluated. */
    @Override
    public boolean matches(SearchCandidate candidate, SearchCriteria criteria) {
        return true;
    }

    @Override
    public void applyBatch(List<SearchCandidate> candidates, SearchCriteria criteria) {
        candidates.removeIf(candidate -> startsBeforePropertyLocalToday(candidate.property(), criteria));
        if (candidates.isEmpty()) {
            return;
        }

        List<Long> roomTypeIds = candidates.stream()
                .flatMap(candidate -> candidate.roomTypes().stream())
                .map(roomTypeCandidate -> roomTypeCandidate.roomType().getId())
                .toList();
        if (roomTypeIds.isEmpty()) {
            return;
        }

        LocalDate firstNight = criteria.nights().get(0);
        LocalDate lastNight = criteria.nights().get(criteria.nights().size() - 1);
        Map<Long, Map<LocalDate, DailyInventory>> rowsByRoomType =
                dailyInventoryRepository.findByRoomTypeIdInAndStayDateBetween(roomTypeIds, firstNight, lastNight).stream()
                        .collect(Collectors.groupingBy(DailyInventory::getRoomTypeId,
                                Collectors.toMap(DailyInventory::getStayDate, row -> row)));

        for (SearchCandidate candidate : candidates) {
            candidate.retainRoomTypes(roomTypeCandidate -> populateAndCheck(
                    roomTypeCandidate, rowsByRoomType.get(roomTypeCandidate.roomType().getId()), criteria));
        }
    }

    /** @return {@code false} (and leaves {@code roomTypeCandidate} unpopulated) if any
     * requested night is unmaterialised or insufficiently available; otherwise populates
     * {@code availableUnits} and {@code stayTotal} and returns {@code true}. */
    private boolean populateAndCheck(RoomTypeCandidate roomTypeCandidate, Map<LocalDate, DailyInventory> rowsByDate,
                                     SearchCriteria criteria) {
        if (rowsByDate == null) {
            return false;
        }
        int minAvailable = Integer.MAX_VALUE;
        BigDecimal stayTotal = BigDecimal.ZERO;
        for (LocalDate night : criteria.nights()) {
            DailyInventory row = rowsByDate.get(night);
            // Missing row = never materialised for this night (§5.3) - excluded, not an error.
            if (row == null || !row.hasCapacityFor(criteria.units())) {
                return false;
            }
            minAvailable = Math.min(minAvailable, row.availableUnits());
            stayTotal = stayTotal.add(row.getPricePerUnit().multiply(BigDecimal.valueOf(criteria.units())));
        }
        roomTypeCandidate.setAvailableUnits(minAvailable);
        roomTypeCandidate.setStayTotal(stayTotal);
        return true;
    }

    private boolean startsBeforePropertyLocalToday(Property property, SearchCriteria criteria) {
        LocalDate propertyToday = LocalDate.now(clock.withZone(property.zone()));
        return criteria.checkIn().isBefore(propertyToday);
    }

    @Override
    public int order() {
        return 70;
    }
}
