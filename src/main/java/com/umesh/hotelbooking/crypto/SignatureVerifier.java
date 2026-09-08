package com.umesh.hotelbooking.crypto;

import com.umesh.hotelbooking.config.WebhookProperties;
import com.umesh.hotelbooking.dto.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * The two checks of design doc 12.3 steps 1, 3 and 4, kept as separate methods so {@code
 * InboundWebhookService} can run the (cheap) dedupe check between them, per that same
 * design-doc ordering.
 */
@Component
public class SignatureVerifier {

    private final HmacSigner signer;
    private final WebhookProperties properties;
    private final Clock clock;

    public SignatureVerifier(HmacSigner signer, WebhookProperties properties, Clock clock) {
        this.signer = signer;
        this.properties = properties;
        this.clock = clock;
    }

    /** Design doc 12.3 step 1: {@code |now − X-Timestamp| <= webhook.inbound.replay-window}. */
    public void verifyTimestamp(long timestampEpochMillis) {
        Instant eventTime = Instant.ofEpochMilli(timestampEpochMillis);
        Instant now = Instant.now(clock);
        Duration delta = Duration.between(eventTime, now).abs();
        if (delta.compareTo(properties.inbound().replayWindow()) > 0) {
            throw new SignatureVerificationException(ErrorCode.REPLAY_WINDOW_EXCEEDED,
                    "X-Timestamp is " + delta + " from now, outside the " + properties.inbound().replayWindow() + " window");
        }
    }

    /**
     * Design doc 12.3 steps 3–4: HMAC-SHA256 over the raw body with the provider's secret,
     * compared constant-time. An unknown {@code providerCode} has no secret, which means
     * verification cannot succeed — rejected as a signature failure, not a 404, so as not to
     * reveal which provider codes this deployment has configured.
     */
    public void verifySignature(String providerCode, byte[] rawBody, String presentedSignature) {
        String secret = properties.secretFor(providerCode);
        if (secret == null || !signer.verify(rawBody, secret, presentedSignature)) {
            throw new SignatureVerificationException(ErrorCode.SIGNATURE_INVALID,
                    "Signature verification failed for provider " + providerCode);
        }
    }
}
