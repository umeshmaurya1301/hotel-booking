package com.umesh.hotelbooking.search;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.dto.SearchPropertiesRequest;
import com.umesh.hotelbooking.dto.SearchResponse;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The N+1 guard, and the test most likely to be skipped and most worth writing (task spec
 * §12). A search implementation that is functionally perfect and issues one query per
 * candidate property has still failed design doc 10.2, and nothing else in this suite would
 * catch it — every other search test asserts on the <em>result</em>, not on how many round
 * trips it took to produce it.
 *
 * <p>Fixtures go through {@link PropertyOnboardingService}, the same choice
 * {@code PropertySearchServiceTest} makes, rather than hand-building the {@code Property} /
 * {@code RoomType} / {@code PropertyGroup} / {@code Owner} entity graph directly — {@code
 * Property.propertyGroup} and {@code PropertyGroup.owner} are both non-null associations, so
 * a manually built fixture would need to duplicate onboarding's own object graph for no
 * benefit. This also means a full {@code @SpringBootTest} rather than a {@code @DataJpaTest}
 * slice: the assertion only needs an {@link EntityManagerFactory} with Hibernate statistics
 * turned on, which works identically either way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "search.max-results=50",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class SearchQueryCountTest {

    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertySearchService propertySearchService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private Clock clock;

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    private void onboard(String city, String roomTypePrefix) {
        onboardingService.onboard(new OnboardPropertyRequest(
                null, "Owner " + System.nanoTime(), null,
                null, null, null, null,
                "Hotel " + System.nanoTime(), city, null, null, null, 4,
                "Asia/Kolkata", "INR", Set.of(Amenity.WIFI),
                List.of(new RoomTypeRequest(roomTypePrefix + " A", 5, 2, new BigDecimal("5000.00")),
                        new RoomTypeRequest(roomTypePrefix + " B", 5, 2, new BigDecimal("6000.00"))),
                null));
    }

    @Test
    void aSearchOverManyCandidatesIssuesABoundedQueryCount() {
        String city = "querycounttestcity" + System.nanoTime();
        for (int i = 0; i < 6; i++) {
            onboard(city, "Room" + i);
        }

        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(2);
        LocalDate checkOut = checkIn.plusDays(1);

        statistics().clear();

        SearchResponse response = propertySearchService.search(new SearchPropertiesRequest(
                city, null, checkIn, checkOut, 1, 1, 0, null, null, null, null));

        assertThat(response.resultCount()).isEqualTo(6);

        long queryCount = statistics().getPrepareStatementCount();
        // Two property fetches (roomTypes, then amenities - see PropertyStore's own
        // Javadoc for why it is two, not one) plus one batched inventory fetch: three,
        // regardless of how many of the six candidates survive to that point. Asserted as
        // "well under one query per candidate" rather than pinned to the exact literal 3, so
        // the test does not become brittle against an incidental extra query that is still
        // O(1) - the property under test is boundedness, not an exact count.
        assertThat(queryCount)
                .as("query count must not scale with candidate count (6 candidates, 2 room types each)")
                .isLessThan(6);
    }
}
