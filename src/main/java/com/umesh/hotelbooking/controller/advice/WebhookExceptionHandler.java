package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.crypto.SignatureVerificationException;
import com.umesh.hotelbooking.webhook.WebhookAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The SYSTEM-category exception mapping (design doc 11.4, task spec §11.2). Scoped to {@code
 * controller.webhook} and ordered ahead of {@link GlobalExceptionHandler} so it wins for any
 * exception a webhook controller throws — {@code GlobalExceptionHandler}'s catch-all would
 * otherwise return 500, and design doc 11.4 is explicit that a non-2xx here triggers
 * aggressive provider retries that amplify the original failure.
 *
 * <p>Exactly one case gets a non-2xx: a bad signature, an unknown provider, or a stale
 * timestamp (all {@link SignatureVerificationException}) return <b>401</b> — a request we
 * will never accept should not be retried, and acking it 200 would tell a misconfigured or
 * hostile sender that its forgery was accepted. Every other exception acks <b>200</b>: a
 * genuine processing bug on our side is ours to fix from the event log, not the provider's to
 * retry into.
 */
@RestControllerAdvice(basePackages = "com.umesh.hotelbooking.controller.webhook")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebhookExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookExceptionHandler.class);

    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<WebhookAck> handleSignatureFailure(SignatureVerificationException exception) {
        log.warn("Webhook rejected: {} [{}]", exception.errorCode(), exception.getMessage());
        return ResponseEntity.status(exception.errorCode().status())
                .body(new WebhookAck(exception.eventId(), "REJECTED"));
    }

    /**
     * The defensive backstop, not the routine path: {@code InboundWebhookService} already
     * catches and acks a genuine processing failure itself (step 7/8 of design doc 12.3), so
     * this only fires for something that escaped that — a bug in the controller itself, or a
     * failure early enough that no event-log row could be written at all, which is why this
     * cannot reliably attach an {@code eventId} or update an outcome the way the service's own
     * handling does.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<WebhookAck> handleUnexpected(Exception exception) {
        log.error("Unhandled exception on the webhook path", exception);
        return ResponseEntity.ok(new WebhookAck(null, "RECEIVED"));
    }
}
