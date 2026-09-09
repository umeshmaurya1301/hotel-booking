package com.umesh.hotelbooking.controller.user;

import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.LedgerEntry;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.gateway.MockCardProvider;
import com.umesh.hotelbooking.gateway.PaymentRequest;
import com.umesh.hotelbooking.repository.LedgerEntryStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import com.umesh.hotelbooking.service.BookingService;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 15.1 test 7: idempotency for payment, not just booking. {@code BookingIdempotencyApiTest}
 * covers {@code POST /api/v1/user/bookings}; nothing covered {@code
 * POST /api/v1/user/bookings/{uid}/pay} before this test, and payment is where a duplicate
 * costs real money. Mirrors that test's structure deliberately, so the two read as a pair.
 *
 * <p>{@link MockCardProvider} is spied on rather than mocked outright — the real simulated
 * SETTLED behaviour is still exercised end to end, and the spy only adds an interaction count
 * to verify on top of it. Counting the provider's own invocations is the assertion that
 * actually proves no double-charge; a passing ledger-row-count assertion alone could still
 * hide a gateway called twice that happened to settle identically both times.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false"
})
class PaymentIdempotencyApiTest {

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
    private BookingService bookingService;
    @Autowired
    private PaymentStore paymentStore;
    @Autowired
    private LedgerEntryStore ledgerEntryStore;
    @Autowired
    private com.umesh.hotelbooking.repository.BookingStore bookingStore;
    @Autowired
    private Clock clock;
    @MockitoSpyBean
    private MockCardProvider mockCardProvider;

    private record Fixture(String roomTypeUid, LocalDate firstNight) {
    }

    private Fixture onboardRoomType(String name) {
        String uniqueName = name + " " + System.nanoTime();
        PropertyResponse response = onboardingService.onboard(new OnboardPropertyRequest(
                null, uniqueName + " Owner", null,
                null, null, null, null,
                uniqueName, "Bengaluru", null, null, null, 4, "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 5, 4, new BigDecimal("8000.00"))),
                null));
        Property property = propertyStore.findByPropertyUid(response.propertyUid()).orElseThrow();
        RoomType roomType = roomTypeStore.findByPropertyId(property.getId()).get(0);
        LocalDate firstNight = LocalDate.now(clock.withZone(property.zone()));
        return new Fixture(roomType.getRoomTypeUid(), firstNight);
    }

    private String bookingUid(Fixture fixture) {
        BookingResponse booking = bookingService.create(
                new RequestMeta(UUID.randomUUID().toString(), ApiType.CREATE_BOOKING, UUID.randomUUID().toString()),
                new CreateBookingRequest(null, fixture.roomTypeUid(), fixture.firstNight(),
                        fixture.firstNight().plusDays(1), 1, 1, 0, null));
        return booking.bookingUid();
    }

    private String payEnvelope(String msgId, InitiatePaymentRequest payload) {
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

    @Test
    void theSameMsgIdPostedTwiceForPaymentReturnsIdenticalDataAndChargesExactlyOnce() throws Exception {
        Fixture fixture = onboardRoomType("Payment Idempotent Hotel");
        String bookingUid = bookingUid(fixture);
        InitiatePaymentRequest payload = new InitiatePaymentRequest(PaymentMethod.CARD, null);
        String msgId = UUID.randomUUID().toString();
        String body = payEnvelope(msgId, payload);

        MvcResult first = mockMvc.perform(post("/api/v1/user/bookings/{bookingUid}/pay", bookingUid)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        MvcResult second = mockMvc.perform(post("/api/v1/user/bookings/{bookingUid}/pay", bookingUid)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        Map<?, ?> firstJson = objectMapper.readValue(first.getResponse().getContentAsString(), Map.class);
        Map<?, ?> secondJson = objectMapper.readValue(second.getResponse().getContentAsString(), Map.class);
        assertThat(secondJson.get("data")).as("a replay returns the identical stored response")
                .isEqualTo(firstJson.get("data"));

        Long bookingId = bookingStore.findByBookingUid(bookingUid).orElseThrow().getId();
        assertThat(paymentStore.findByBookingId(bookingId)).hasSize(1);
        assertThat(ledgerEntryStore.findByBookingIdOrderByOccurredAtAsc(bookingId))
                .extracting(LedgerEntry::getType)
                .containsExactly(EntryType.CHARGE);

        // The assertion that actually proves no double-charge: the gateway itself was called once.
        verify(mockCardProvider, times(1)).initiate(any(PaymentRequest.class));
    }

    @Test
    void theSameMsgIdWithAChangedPayloadIsRejectedAsAMismatch() throws Exception {
        Fixture fixture = onboardRoomType("Payment Mismatch Hotel");
        String bookingUid = bookingUid(fixture);
        String msgId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/user/bookings/{bookingUid}/pay", bookingUid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payEnvelope(msgId, new InitiatePaymentRequest(PaymentMethod.CARD, null))))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(post("/api/v1/user/bookings/{bookingUid}/pay", bookingUid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payEnvelope(msgId, new InitiatePaymentRequest(PaymentMethod.UPI, null))))
                .andExpect(status().is(422));
    }
}
