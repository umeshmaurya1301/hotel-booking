package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The highest-value test in this phase: it proves the {@code msgId} migration of the task
 * spec's §3 actually closed the double-booking hole — before this phase, {@code
 * CreateBookingRequest} carried no idempotency key at all, so a retried create held a second
 * set of room-nights. Exercises envelope, request context, idempotency and reservation in one
 * path (design doc 15.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false"
})
class BookingIdempotencyApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyStore propertyStore;
    @Autowired
    private RoomTypeStore roomTypeStore;
    @Autowired
    private DailyInventoryStore dailyInventoryStore;
    @Autowired
    private Clock clock;

    private record Fixture(String roomTypeUid, Long roomTypeId, LocalDate firstNight) {
    }

    private Fixture onboardRoomType(String name) {
        String uniqueName = name + " " + System.nanoTime();
        PropertyResponse response = onboardingService.onboard(new OnboardPropertyRequest(
                null, uniqueName + " Owner", null,
                null, null, null,
                null,
                uniqueName, "Bengaluru", null, null, null, 4, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 5, 4, new BigDecimal("8000.00"))),
                null));
        Property property = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        RoomType roomType = roomTypeStore.findByPropertyId(property.getId()).get(0);
        LocalDate firstNight = LocalDate.now(clock.withZone(property.zone()));
        return new Fixture(roomType.getRoomTypeUid(), roomType.getId(), firstNight);
    }

    private String envelope(String msgId, CreateBookingRequest payload) {
        return """
                {
                  "msgId": "%s",
                  "timestamp": "2026-09-08T10:00:00Z",
                  "channel": "WEB",
                  "version": "v1",
                  "payload": %s
                }
                """.formatted(msgId, objectMapper.writeValueAsString(payload));
    }

    private int bookedUnits(Fixture fixture) {
        return dailyInventoryStore.findByRoomTypeIdAndStayDate(fixture.roomTypeId(), fixture.firstNight())
                .orElseThrow().getBookedUnits();
    }

    @Test
    void theSameMsgIdPostedTwiceReturnsIdenticalDataAndHoldsInventoryOnce() throws Exception {
        Fixture fixture = onboardRoomType("Idempotent Hotel");
        CreateBookingRequest payload = new CreateBookingRequest(
                null, fixture.roomTypeUid(), fixture.firstNight(), fixture.firstNight().plusDays(1), 1, 1, 0, null);
        String msgId = UUID.randomUUID().toString();
        String body = envelope(msgId, payload);

        MvcResult first = mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        Map<?, ?> firstJson = objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
        Map<?, ?> secondJson = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
        assertThat(secondJson.get("data")).as("a replay returns the identical stored response")
                .isEqualTo(firstJson.get("data"));

        assertThat(bookedUnits(fixture)).as("inventory is held exactly once, not once per replay").isEqualTo(1);
    }

    @Test
    void aDifferentMsgIdWithTheSamePayloadHoldsASecondTime() throws Exception {
        Fixture fixture = onboardRoomType("Distinct MsgId Hotel");
        CreateBookingRequest payload = new CreateBookingRequest(
                null, fixture.roomTypeUid(), fixture.firstNight(), fixture.firstNight().plusDays(1), 1, 1, 0, null);

        mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(UUID.randomUUID().toString(), payload)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(envelope(UUID.randomUUID().toString(), payload)))
                .andExpect(status().isCreated());

        assertThat(bookedUnits(fixture)).as("two independent requests hold inventory twice").isEqualTo(2);
    }

    @Test
    void theSameMsgIdWithAChangedPayloadIsRejectedAsAMismatch() throws Exception {
        Fixture fixture = onboardRoomType("Mismatch Hotel");
        String msgId = UUID.randomUUID().toString();
        CreateBookingRequest original = new CreateBookingRequest(
                null, fixture.roomTypeUid(), fixture.firstNight(), fixture.firstNight().plusDays(1), 1, 1, 0, null);
        CreateBookingRequest changed = new CreateBookingRequest(
                null, fixture.roomTypeUid(), fixture.firstNight(), fixture.firstNight().plusDays(1), 2, 1, 0, null);

        mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(envelope(msgId, original)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/user/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(envelope(msgId, changed)))
                .andExpect(status().is(422));
    }
}
