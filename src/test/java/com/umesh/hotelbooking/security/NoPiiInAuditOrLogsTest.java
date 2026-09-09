package com.umesh.hotelbooking.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.umesh.hotelbooking.crypto.HmacSigner;
import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.dto.InitiatePaymentRequest;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PaymentResponse;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.IdempotencyRecord;
import com.umesh.hotelbooking.entity.PaymentMethod;
import com.umesh.hotelbooking.entity.PaymentState;
import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.gateway.SimulatedOutcome;
import com.umesh.hotelbooking.repository.IdempotencyRecordStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.PaymentStatusCheckStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import com.umesh.hotelbooking.service.BookingService;
import com.umesh.hotelbooking.service.MutableClock;
import com.umesh.hotelbooking.service.PaymentReconciliationService;
import com.umesh.hotelbooking.service.PaymentService;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.web.RequestMeta;
import com.umesh.hotelbooking.webhook.WebhookEventLog;
import com.umesh.hotelbooking.repository.WebhookEventLogStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The leak test design doc 12.6.7 names, and the one that would fail today if {@code
 * PayloadRedactor} were disabled — verified by hand: temporarily stubbing {@link
 * com.umesh.hotelbooking.security.PayloadRedactor#redact} to return its input unchanged makes
 * this test fail on the {@code payment_status_check} and {@code webhook_event_log} assertions,
 * which is what proves it is testing something real rather than passing vacuously.
 *
 * <p>Drives a full pay-with-card flow — create, pay, a reconciliation poll that carries the
 * synthetic PAN/CVV/email/phone payload of design doc 12.6.6, and an inbound webhook — then
 * asserts none of {@code payment_status_check.response_summary}, {@code
 * webhook_event_log.payload}, {@code idempotency_records.response_body} or captured log
 * output contains the raw PAN, the CVV key or value, the raw email, or the raw phone.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "payment.reconciliation.enabled=false",
        "webhook.providers.MOCK_CARD.secret=dev-secret-card"
})
class NoPiiInAuditOrLogsTest {

    private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");
    private static final String RAW_PAN = "4111111111111111";
    private static final String RAW_CVV = "123";
    private static final String RAW_EMAIL = "asha@example.com";
    private static final String RAW_PHONE = "+919876543210";

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(START, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private HmacSigner signer;
    @Autowired
    private PropertyOnboardingService onboardingService;
    @Autowired
    private PropertyStore propertyStore;
    @Autowired
    private RoomTypeStore roomTypeStore;
    @Autowired
    private BookingService bookingService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentStore paymentStore;
    @Autowired
    private PaymentReconciliationService reconciliationService;
    @Autowired
    private PaymentStatusCheckStore statusCheckStore;
    @Autowired
    private WebhookEventLogStore webhookEventLogStore;
    @Autowired
    private IdempotencyRecordStore idempotencyRecordStore;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void attachLogCapture() {
        logCapture = new ListAppender<>();
        logCapture.start();
        rootLogger().addAppender(logCapture);
    }

    @AfterEach
    void detachLogCapture() {
        rootLogger().detachAppender(logCapture);
    }

    private Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @Test
    void noRawPanCvvEmailOrPhoneReachesAnyAuditRowOrLogLine() throws Exception {
        clock().setTo(START);

        PropertyResponse property = onboardingService.onboard(new OnboardPropertyRequest(
                null, "Leak Test Owner", null,
                null, null, null, null,
                "Leak Test Hotel " + System.nanoTime(), "Bengaluru", null, null, null, 4,
                "Asia/Kolkata", "INR", null,
                List.of(new RoomTypeRequest("Deluxe King", 5, 4, new BigDecimal("8000.00"))),
                null));
        RoomType roomType = roomTypeStore.findByPropertyId(
                propertyStore.findByPropertyUid(property.propertyUid()).orElseThrow().getId()).get(0);
        LocalDate checkIn = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1);

        RequestMeta bookingMeta = new RequestMeta(UUID.randomUUID().toString(), ApiType.CREATE_BOOKING, UUID.randomUUID().toString());
        BookingResponse booking = bookingService.create(bookingMeta, new CreateBookingRequest(
                null, roomType.getRoomTypeUid(), checkIn, checkIn.plusDays(1), 1, 1, 0, null));

        // A card payment that does not settle synchronously, so the reconciliation ladder
        // polls the gateway and writes a payment_status_check row from the synthetic
        // PAN/CVV/email/phone payload of design doc 12.6.6.
        RequestMeta payMeta = new RequestMeta(UUID.randomUUID().toString(), ApiType.PAY_BOOKING, UUID.randomUUID().toString());
        PaymentResponse payment = paymentService.pay(booking.bookingUid(), payMeta,
                new InitiatePaymentRequest(PaymentMethod.CARD, SimulatedOutcome.STUCK_THEN_SETTLE));
        assertThat(payment.state()).isEqualTo(PaymentState.UNKNOWN);

        clock().advance(Duration.ofSeconds(40));
        reconciliationService.run();
        clock().advance(Duration.ofMinutes(2));
        reconciliationService.run();

        String providerReference = paymentStore.findByPaymentUid(payment.paymentUid()).orElseThrow().getProviderReference();

        // An inbound webhook carrying the same shape of raw instrument data a real callback
        // would (design doc 12.6.6), so webhook_event_log gets exercised too.
        String eventId = UUID.randomUUID().toString();
        byte[] body = """
                {"eventId":"%s","eventType":"PAYMENT_SUCCESS","providerCode":"MOCK_CARD",\
                "eventTime":"2026-09-08T12:00:00Z","version":"v1",\
                "payload":{"providerReference":"%s","cardNumber":"%s","cvv":"%s",\
                "email":"%s","phone":"%s"}}"""
                .formatted(eventId, providerReference, RAW_PAN, RAW_CVV, RAW_EMAIL, RAW_PHONE)
                .getBytes(StandardCharsets.UTF_8);
        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", signer.sign(body, "dev-secret-card"))
                        .header("X-Timestamp", Instant.now(clock).toEpochMilli())
                        .content(body))
                .andExpect(status().isOk());

        // --- payment_status_check ---
        List<PaymentStatusCheck> statusChecks =
                statusCheckStore.findByPaymentIdOrderByAttemptNoAsc(
                        paymentStore.findByPaymentUid(payment.paymentUid()).orElseThrow().getId());
        assertThat(statusChecks).isNotEmpty();
        for (PaymentStatusCheck check : statusChecks) {
            assertNoRawSecrets(check.getResponseSummary(), "payment_status_check.response_summary");
        }

        // --- webhook_event_log ---
        WebhookEventLog logRow = webhookEventLogStore.findByProviderCodeAndEventId("MOCK_CARD", eventId).orElseThrow();
        assertNoRawSecrets(logRow.getPayload(), "webhook_event_log.payload");

        // --- idempotency_records ---
        for (IdempotencyRecord record : idempotencyRecordStore.findAll()) {
            assertNoRawSecrets(record.getResponseBody(), "idempotency_records.response_body");
        }

        // --- captured log output ---
        String allLogs = logCapture.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertNoRawSecrets(allLogs, "captured log output");
    }

    private void assertNoRawSecrets(String haystack, String location) {
        if (haystack == null) {
            return;
        }
        assertThat(haystack).as("%s must not contain the raw PAN", location).doesNotContain(RAW_PAN);
        assertThat(haystack.toLowerCase()).as("%s must not contain the cvv key", location).doesNotContain("cvv");
        assertThat(haystack).as("%s must not contain the raw CVV value", location).doesNotContain("\"" + RAW_CVV + "\"");
        assertThat(haystack).as("%s must not contain the raw email", location).doesNotContain(RAW_EMAIL);
        assertThat(haystack).as("%s must not contain the raw phone", location).doesNotContain(RAW_PHONE);
    }
}
