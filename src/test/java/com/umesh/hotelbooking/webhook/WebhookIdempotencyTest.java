package com.umesh.hotelbooking.webhook;

import com.umesh.hotelbooking.crypto.HmacSigner;
import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.EntryType;
import com.umesh.hotelbooking.entity.Payment;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.LedgerEntryRepository;
import com.umesh.hotelbooking.repository.PaymentRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import com.umesh.hotelbooking.service.BookingService;
import com.umesh.hotelbooking.service.PaymentService;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotency layer (c) of design doc 8c, end to end: the same {@code (providerCode,
 * eventId)} delivered twice must confirm the booking exactly once, and two genuinely
 * different {@code eventId}s carrying {@code PAYMENT_SUCCESS} for an already-settled payment
 * must still write only one ledger entry — the FSM-level no-op the unique-constraint dedupe
 * alone cannot guarantee, since a provider can legitimately mint a fresh {@code eventId} per
 * delivery attempt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "webhook.providers.MOCK_CARD.secret=dev-secret-card"
})
class WebhookIdempotencyTest {

    private static final String SECRET = "dev-secret-card";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HmacSigner signer;
    @Autowired
    private WebhookEventLogRepository logRepository;
    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private RoomTypeRepository roomTypeRepository;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;
    @Autowired
    private Clock clock;

    private String bookingUid;

    private String envelope(String eventId, String eventType, String providerReference) {
        return """
                {"eventId":"%s","eventType":"%s","providerCode":"MOCK_CARD",\
                "eventTime":"2026-09-08T12:00:00Z","version":"v1",\
                "payload":{"providerReference":"%s"}}""".formatted(eventId, eventType, providerReference);
    }

    private void deliver(String eventId, String providerReference) throws Exception {
        byte[] body = envelope(eventId, "PAYMENT_SUCCESS", providerReference).getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", signer.sign(body, SECRET))
                        .header("X-Timestamp", Instant.now(clock).toEpochMilli())
                        .content(body))
                .andExpect(status().isOk());
    }

    /** A payment stuck PENDING at the gateway, never settled synchronously — exactly the
     * PAYMENT_UNKNOWN case an async webhook exists to resolve. */
    private String createUnsettledPayment() {
        PropertyResponse property = onboardingService.onboard(new OnboardPropertyRequest(
                null, "Webhook Idempotency Owner", null,
                null, null, null, null,
                "Webhook Idempotency Hotel " + System.nanoTime(), "Bengaluru", null, null, null, 4,
                "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 5, 4, new BigDecimal("8000.00"))),
                null));
        RoomType roomType = roomTypeRepository.findByPropertyId(
                propertyRepository.findByPropertyUid(property.propertyUid()).orElseThrow().getId()).get(0);
        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);

        RequestMeta bookingMeta = new RequestMeta(UUID.randomUUID().toString(), ApiType.CREATE_BOOKING, UUID.randomUUID().toString());
        BookingResponse booking = bookingService.create(bookingMeta, new CreateBookingRequest(
                null, roomType.getRoomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null));

        RequestMeta payMeta = new RequestMeta(UUID.randomUUID().toString(), ApiType.PAY_BOOKING, UUID.randomUUID().toString());
        PaymentResponse payment = paymentService.pay(booking.bookingUid(), payMeta,
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.STUCK_FOREVER));

        Payment paymentEntity = paymentRepository.findByPaymentUid(payment.paymentUid()).orElseThrow();
        this.bookingUid = booking.bookingUid();
        return paymentEntity.getProviderReference();
    }

    @Test
    void theSameEventIdDeliveredTwiceConfirmsTheBookingExactlyOnce() throws Exception {
        String providerReference = createUnsettledPayment();
        String eventId = UUID.randomUUID().toString();

        deliver(eventId, providerReference);
        deliver(eventId, providerReference);

        assertThat(logRepository.findByProviderCodeAndEventId("MOCK_CARD", eventId)).isPresent();
        assertThat(logRepository.findAll().stream()
                .filter(row -> row.getEventId().equals(eventId)).count())
                .as("exactly one webhook_event_log row for this eventId").isEqualTo(1);

        Booking booking = bookingRepository.findByBookingUid(bookingUid).orElseThrow();
        assertThat(booking.getState()).isEqualTo(BookingState.CONFIRMED);

        long chargeEntries = ledgerEntryRepository.findByBookingIdOrderByOccurredAtAsc(booking.getId()).stream()
                .filter(entry -> entry.getType() == EntryType.CHARGE)
                .count();
        assertThat(chargeEntries).as("exactly one ledger entry despite two deliveries").isEqualTo(1);
    }

    @Test
    void twoDifferentEventIdsForAnAlreadySettledPaymentStillWriteOneLedgerEntry() throws Exception {
        String providerReference = createUnsettledPayment();

        deliver(UUID.randomUUID().toString(), providerReference);
        Booking confirmedOnce = bookingRepository.findByBookingUid(bookingUid).orElseThrow();
        assertThat(confirmedOnce.getState()).isEqualTo(BookingState.CONFIRMED);

        // A second, genuinely different eventId for the same already-settled payment: the
        // (provider_code, event_id) constraint does not catch this — it is a different key —
        // so the FSM-level guard in PaymentSettlementService.settle is what must catch it.
        deliver(UUID.randomUUID().toString(), providerReference);

        Booking stillConfirmed = bookingRepository.findByBookingUid(bookingUid).orElseThrow();
        assertThat(stillConfirmed.getState()).isEqualTo(BookingState.CONFIRMED);

        long chargeEntries = ledgerEntryRepository.findByBookingIdOrderByOccurredAtAsc(stillConfirmed.getId()).stream()
                .filter(entry -> entry.getType() == EntryType.CHARGE)
                .count();
        assertThat(chargeEntries).as("still one ledger entry across two different eventIds").isEqualTo(1);
    }
}
