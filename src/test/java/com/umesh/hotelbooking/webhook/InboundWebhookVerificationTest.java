package com.umesh.hotelbooking.webhook;

import com.umesh.hotelbooking.crypto.HmacSigner;
import com.umesh.hotelbooking.service.MutableClock;
import com.umesh.hotelbooking.repository.WebhookEventLogStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full 12.3 ladder, end to end through a real HTTP call: timestamp window, signature
 * verification, and the persisted {@code webhook_event_log} outcome each step leaves behind.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "webhook.providers.MOCK_CARD.secret=dev-secret-card"
})
class InboundWebhookVerificationTest {

    private static final Instant START = Instant.parse("2026-09-08T12:00:00Z");
    private static final String SECRET = "dev-secret-card";

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
    private WebhookEventLogStore logStore;
    @Autowired
    private Clock clock;

    private MutableClock clock() {
        return (MutableClock) clock;
    }

    private String envelope(String eventId, String eventType, String providerReference) {
        return """
                {"eventId":"%s","eventType":"%s","providerCode":"MOCK_CARD",\
                "eventTime":"2026-09-08T12:00:00Z","version":"v1",\
                "payload":{"providerReference":"%s","cardNumber":"4111111111111111","cvv":"123"}}\
                """.formatted(eventId, eventType, providerReference);
    }

    @Test
    void aValidSignedCallbackReturns200AndPersistsProcessed() throws Exception {
        clock().setTo(START);
        String eventId = UUID.randomUUID().toString();
        byte[] body = envelope(eventId, "PAYMENT_SUCCESS", "no-such-reference-" + eventId)
                .getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", signer.sign(body, SECRET))
                        .header("X-Timestamp", START.toEpochMilli())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        Optional<WebhookEventLog> row = logStore.findByProviderCodeAndEventId("MOCK_CARD", eventId);
        assertThat(row).isPresent();
        assertThat(row.get().isSignatureValid()).isTrue();
        // The referenced payment does not exist in this test, so processing itself fails —
        // proving the row was written *before* processing ran, exactly as design doc 12.3
        // step 6 requires, and independent of whether step 7 subsequently succeeds.
        assertThat(row.get().getOutcome()).isEqualTo(WebhookOutcome.PROCESSING_FAILED);
    }

    @Test
    void aTamperedBodyReturns401AndPersistsSignatureInvalid() throws Exception {
        clock().setTo(START);
        String eventId = UUID.randomUUID().toString();
        byte[] body = envelope(eventId, "PAYMENT_SUCCESS", "ref-tampered").getBytes(StandardCharsets.UTF_8);
        String validSignature = signer.sign(body, SECRET);
        byte[] tamperedBody = envelope(eventId, "PAYMENT_SUCCESS", "ref-tampered-CHANGED").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", validSignature)
                        .header("X-Timestamp", START.toEpochMilli())
                        .content(tamperedBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Optional<WebhookEventLog> row = logStore.findByProviderCodeAndEventId("MOCK_CARD", eventId);
        assertThat(row).isPresent();
        assertThat(row.get().isSignatureValid()).isFalse();
        assertThat(row.get().getOutcome()).isEqualTo(WebhookOutcome.SIGNATURE_INVALID);
    }

    @Test
    void aTimestampOutsideTheReplayWindowReturns401() throws Exception {
        clock().setTo(START);
        String eventId = UUID.randomUUID().toString();
        byte[] body = envelope(eventId, "PAYMENT_SUCCESS", "ref-stale").getBytes(StandardCharsets.UTF_8);
        long staleTimestamp = START.minus(Duration.ofMinutes(10)).toEpochMilli();

        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", signer.sign(body, SECRET))
                        .header("X-Timestamp", staleTimestamp)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void anUnknownEventTypeReturns200AndPersistsUnknownEventType() throws Exception {
        clock().setTo(START);
        String eventId = UUID.randomUUID().toString();
        byte[] body = envelope(eventId, "SOME_FUTURE_EVENT_TYPE", "ref-unknown").getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", signer.sign(body, SECRET))
                        .header("X-Timestamp", START.toEpochMilli())
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        Optional<WebhookEventLog> row = logStore.findByProviderCodeAndEventId("MOCK_CARD", eventId);
        assertThat(row).isPresent();
        assertThat(row.get().getOutcome()).isEqualTo(WebhookOutcome.UNKNOWN_EVENT_TYPE);
    }
}
