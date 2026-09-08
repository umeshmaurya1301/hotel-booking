package com.umesh.hotelbooking.webhook;

import com.umesh.hotelbooking.config.WebhookProperties;
import com.umesh.hotelbooking.crypto.HmacSigner;
import com.umesh.hotelbooking.event.BookingCancelledEvent;
import com.umesh.hotelbooking.event.BookingCompletedEvent;
import com.umesh.hotelbooking.event.BookingCreatedEvent;
import com.umesh.hotelbooking.event.BookingExpiredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Signed outbound merchant notifications (design doc 12.4) — the smallest deliverable of this
 * phase, deliberately kept that way.
 *
 * <p>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} is the house pattern —
 * {@code BookingCancelledListener} and {@code PropertyOnboardedListener} both use it, and
 * design doc 15.1 requires it here too: never notify a merchant about a booking that then
 * rolled back.
 *
 * <p>{@code @Async} moves delivery off the request thread — a guest's booking response must
 * not wait on a merchant's endpoint — backed by a virtual-thread executor via {@code
 * spring.threads.virtual.enabled} (see {@code ResilienceConfig}).
 *
 * <p>Disabled by default ({@code webhook.outbound.enabled: false}): with no configured
 * merchant URL there is nothing to call, and every event below only logs what it would have
 * sent. This is also what keeps every booking test in this codebase fast and non-flaky — none
 * of them depend on outbound delivery succeeding, or running at all.
 *
 * <p>The payload carries <b>no personal data</b> — booking uid, property uid, state, amount.
 * Design doc 12.6.3's rule about append-only tables applies with even more force to data
 * leaving the system entirely.
 */
@Component
public class WebhookDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatcher.class);
    private static final String EVENT_VERSION = "v1";
    private static final String SOURCE = "HOTEL_BOOKING";

    private final WebhookProperties properties;
    private final HmacSigner signer;
    private final WebhookHttpSender sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public WebhookDispatcher(WebhookProperties properties, HmacSigner signer, WebhookHttpSender sender,
                             ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.signer = signer;
        this.sender = sender;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCreated(BookingCreatedEvent event) {
        dispatch("BOOKING_CREATED", event.bookingUid(),
                new BookingNotification(event.bookingUid(), event.propertyUid(), "CREATED", null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        dispatch("BOOKING_CANCELLED", event.bookingUid(),
                new BookingNotification(event.bookingUid(), null, "CANCELLED", event.refundAmount()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCompleted(BookingCompletedEvent event) {
        dispatch("BOOKING_COMPLETED", event.bookingUid(),
                new BookingNotification(event.bookingUid(), null, "COMPLETED", null));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingExpired(BookingExpiredEvent event) {
        dispatch("BOOKING_EXPIRED", event.bookingUid(),
                new BookingNotification(event.bookingUid(), null, "EXPIRED", null));
    }

    private void dispatch(String eventType, String bookingUid, BookingNotification payload) {
        if (!properties.outbound().enabled()) {
            log.debug("Outbound webhooks disabled; would have sent {} for booking {}", eventType, bookingUid);
            return;
        }

        Instant now = Instant.now(clock);
        WebhookEnvelope<BookingNotification> envelope = new WebhookEnvelope<>(
                UUID.randomUUID().toString(), eventType, SOURCE, now, EVENT_VERSION, payload);
        byte[] body = objectMapper.writeValueAsBytes(envelope);
        String signature = signer.sign(body, properties.outbound().secret());

        try {
            sender.send(properties.outbound().url(), body, signature, now.toEpochMilli());
        } catch (RestClientException e) {
            // Retries are already exhausted by the time this is caught (WebhookHttpSender's
            // own @Retryable). Never log the signature — only the outcome (design doc 12.1's
            // "never log the secret" applies to the signature itself just as much).
            log.warn("Outbound webhook delivery failed after retries: eventType={} bookingUid={} error={}",
                    eventType, bookingUid, e.getMessage());
        }
    }

    /** No personal data — booking uid, property uid, state, amount only. */
    private record BookingNotification(String bookingUid, String propertyUid, String state, BigDecimal amount) {
    }
}
