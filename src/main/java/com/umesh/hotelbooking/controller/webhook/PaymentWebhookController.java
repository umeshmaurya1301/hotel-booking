package com.umesh.hotelbooking.controller.webhook;

import com.umesh.hotelbooking.web.Api;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.ApiType;
import com.umesh.hotelbooking.webhook.InboundWebhookService;
import com.umesh.hotelbooking.webhook.WebhookAck;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The SYSTEM-category inbound webhook (design doc 11.4, 12.1–12.3). Authenticated by HMAC
 * signature, not the {@code X-Role} header {@link com.umesh.hotelbooking.web.RoleInterceptor}
 * checks everywhere else — see {@code WebConfig}'s explicit exclusion of this path from that
 * interceptor.
 *
 * <p>The body is read as raw {@code byte[]}, not deserialized by Spring: the HMAC is computed
 * over the exact bytes the provider signed, and any deserialization step in between risks
 * reserialising to a byte-for-byte different (if semantically identical) representation. See
 * this phase's own findings for why this was chosen over {@code
 * ContentCachingRequestWrapper} — design doc 12.2's literal suggestion.
 *
 * <p>{@code RequestEnvelopeAdvice.supports()} only matches {@code ApiRequest} body types, so
 * this {@code byte[]} body is untouched by it, and {@code ResponseEnvelopeAdvice} is scoped
 * away from {@code controller.webhook} entirely — the response body below is a bare {@link
 * WebhookAck}, the provider's own contract, not ours.
 */
@RestController
@RequestMapping("/api/v1/webhooks/payment/{providerCode}")
public class PaymentWebhookController {

    private final InboundWebhookService inboundWebhookService;
    private final ApiContext apiContext;

    public PaymentWebhookController(InboundWebhookService inboundWebhookService, ApiContext apiContext) {
        this.inboundWebhookService = inboundWebhookService;
        this.apiContext = apiContext;
    }

    @PostMapping
    @Api(ApiType.PAYMENT_WEBHOOK)
    public ResponseEntity<WebhookAck> receive(
            @PathVariable String providerCode,
            @RequestHeader("X-Signature") String signature,
            @RequestHeader("X-Timestamp") long timestamp,
            @RequestBody byte[] rawBody) {
        WebhookAck ack = inboundWebhookService.receive(
                providerCode, signature, timestamp, rawBody, apiContext.getCorrelationId());
        return ResponseEntity.ok(ack);
    }
}
