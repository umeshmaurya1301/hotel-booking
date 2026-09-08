package com.umesh.hotelbooking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * Webhook settings (design doc 12.1–12.5): the inbound replay window, one HMAC secret per
 * provider, and outbound delivery.
 *
 * <p>The secrets below are development placeholders, committed only because this is a demo
 * exercise. In production they come from the environment or a secret manager, never from a
 * committed YAML file — design doc 12.5 deliberately does not invent a key-management story
 * beyond externalised configuration, and this is exactly that externalisation, not a
 * statement that plaintext YAML secrets are an acceptable production posture.
 *
 * @param inbound the replay-window check of design doc 12.3 step 1
 * @param providers one HMAC secret per {@code providerCode}. An unrecognised code has no
 *     secret, which means verification cannot succeed — see {@code
 *     SignatureVerifier#verifySignature}, which rejects it as a signature failure rather than
 *     a 404, so as not to reveal which provider codes are configured.
 * @param outbound merchant notification delivery, disabled by default (design doc 12.4)
 */
@ConfigurationProperties(prefix = "webhook")
public record WebhookProperties(Inbound inbound, Map<String, Provider> providers, Outbound outbound) {

    public WebhookProperties {
        inbound = inbound == null ? new Inbound(null) : inbound;
        providers = providers == null ? Map.of() : providers;
        outbound = outbound == null ? new Outbound(false, null, null, null) : outbound;
    }

    /** @return the configured secret for {@code providerCode}, or {@code null} if unknown */
    public String secretFor(String providerCode) {
        Provider provider = providers.get(providerCode);
        return provider == null ? null : provider.secret();
    }

    public record Inbound(Duration replayWindow) {
        public Inbound {
            replayWindow = replayWindow == null ? Duration.ofMinutes(5) : replayWindow;
        }
    }

    public record Provider(String secret) {
    }

    /**
     * @param secret signs outbound notifications (design doc 12.4: "the same mechanism" as
     *     inbound, not necessarily the same key). Not part of the YAML shape the task spec's
     *     own §7.2 lists verbatim — added because {@code HmacSigner.sign} needs some secret to
     *     sign with, and design doc 12.1's "one envelope, one signer, both directions" is about
     *     the mechanism, not a shared key across trust boundaries that have no reason to share
     *     one.
     */
    public record Outbound(boolean enabled, String url, String secret, Retry retry) {
        public Outbound {
            url = url == null ? "" : url;
            secret = secret == null ? "dev-secret-outbound" : secret;
            retry = retry == null ? new Retry(4, Duration.ofMillis(200), Duration.ofSeconds(5)) : retry;
        }

        public record Retry(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        }
    }
}
