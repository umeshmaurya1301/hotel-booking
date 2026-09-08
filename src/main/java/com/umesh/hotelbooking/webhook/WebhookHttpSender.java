package com.umesh.hotelbooking.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.concurrent.TimeUnit;

/**
 * The actual outbound HTTP POST, isolated on its own bean so {@code @Retryable} applies —
 * exactly the same reason {@code PaymentGatewayClient} is a separate bean from its callers
 * (design doc 7.4): a call from {@code WebhookDispatcher} to itself would bypass the Spring
 * proxy that {@code @Retryable} depends on, silently turning the retry into a no-op.
 *
 * <p>Retry configuration mirrors {@code webhook.outbound.retry} as a literal, the same
 * house pattern {@code PaymentGatewayClient} uses and explains: {@code @Retryable}'s numeric
 * attributes must be compile-time constants.
 */
@Component
public class WebhookHttpSender {

    private static final Logger log = LoggerFactory.getLogger(WebhookHttpSender.class);

    private final RestClient restClient = RestClient.create();

    @Retryable(
            includes = {RestClientException.class},
            maxRetries = 4,
            delay = 200,
            maxDelay = 5000,
            multiplier = 2.0,
            jitter = 100,
            timeUnit = TimeUnit.MILLISECONDS)
    public void send(String url, byte[] body, String signature, long timestampEpochMillis) {
        restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Signature", signature)
                .header("X-Timestamp", String.valueOf(timestampEpochMillis))
                .body(body)
                .retrieve()
                .toBodilessEntity();
        log.info("Outbound webhook delivered to {}", url);
    }
}
