package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fixture support for the concurrency tests.
 *
 * <p>Each test onboards its own property, so tests never share a room-night and no cleanup
 * between them is required — which matters because these tests cannot be {@code @Transactional}
 * and so cannot lean on rollback for isolation.
 */
abstract class AbstractBookingConcurrencyTestSupport {

    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private Clock clock;

    /** Identifiers for the room type under test, plus the first night that has inventory. */
    protected record Fixture(String propertyUid, String roomTypeUid, Long roomTypeId, LocalDate firstNight) {
    }

    protected Fixture onboardRoomType(String propertyName, int totalUnits) {
        String uniqueName = propertyName + " " + System.nanoTime();
        PropertyResponse response = onboardingService.onboard(new OnboardPropertyRequest(
                null, uniqueName + " Owner", null,
                null, null, null,
                null,
                uniqueName, "Bengaluru", null, null, null, 4, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", totalUnits, 4, new BigDecimal("8000.00"))),
                null));

        Property property = propertyRepository.findByPropertyUid(response.propertyUid()).orElseThrow();
        RoomType roomType = roomTypeRepository.findByPropertyId(property.getId()).get(0);

        // Materialisation starts at the property's local today (design doc 4.5).
        LocalDate firstNight = LocalDate.now(clock.withZone(property.zone()));

        return new Fixture(response.propertyUid(), roomType.getRoomTypeUid(), roomType.getId(), firstNight);
    }

    /**
     * A fresh idempotency key per call. Concurrency tests in particular race several threads
     * with an otherwise-identical {@code CreateBookingRequest}; a shared {@code msgId} across
     * them would make the idempotency layer collapse the race into "one request, N replays"
     * instead of the independent, competing requests these tests mean to exercise.
     */
    protected static RequestMeta freshMeta() {
        return new RequestMeta(UUID.randomUUID().toString(), ApiType.CREATE_BOOKING, UUID.randomUUID().toString());
    }
}
