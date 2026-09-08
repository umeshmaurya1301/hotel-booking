package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.dto.ApiResponse;
import com.umesh.hotelbooking.dto.PendingAware;
import com.umesh.hotelbooking.web.ApiContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Clock;
import java.time.Instant;

/**
 * Wraps every plain DTO a {@code controller} method returns in an {@link ApiResponse} (design
 * doc 11.2), so controllers never hand-build the envelope themselves.
 *
 * <p>Scoped to {@code basePackages = {"...controller.admin", "...controller.user"}} rather
 * than a path-prefix check — this is what keeps {@code /actuator/health} and the H2 console
 * (both enabled in {@code application.yml}) out of the envelope without special-casing their
 * paths here.
 *
 * <p>{@code controller.webhook} is deliberately excluded (Phase 7, task spec §11.1). A
 * webhook body is a {@code WebhookEnvelope}, not an {@code ApiRequest}, so wrapping its
 * response would produce {@code msgId: null} — structurally wrong, not just superfluous — and
 * the provider on the other end has its own contract it did not agree to share with ours.
 * Listing the two packages explicitly, rather than the parent {@code controller} package,
 * also means a future {@code controller.webhook} class is never silently re-captured by
 * widening this list back out.
 */
@RestControllerAdvice(basePackages = {
        "com.umesh.hotelbooking.controller.admin",
        "com.umesh.hotelbooking.controller.user"})
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private final ApiContext apiContext;
    private final Clock clock;

    public ResponseEnvelopeAdvice(ApiContext apiContext, Clock clock) {
        this.apiContext = apiContext;
        this.clock = clock;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                   Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                   ServerHttpRequest request, ServerHttpResponse response) {
        // Already enveloped by GlobalExceptionHandler — wrapping again would double-nest it.
        if (body instanceof ApiResponse<?> || body instanceof ProblemDetail) {
            return body;
        }
        // No current handler returns a bare String, but StringHttpMessageConverter is selected
        // ahead of the Jackson converter when one does, and handing it an ApiResponse instead
        // of a String throws ClassCastException deep inside Spring — guard against it anyway.
        if (body instanceof String) {
            return body;
        }

        Instant now = Instant.now(clock);
        String msgId = apiContext.getMsgId();
        String correlationId = apiContext.getCorrelationId();

        // A single PENDING-aware payload renders as PENDING; a collection of them (e.g. the
        // stuck-payments list) rendered SUCCESS - the list itself was retrieved successfully,
        // even though some of its elements are individually pending. A deliberate judgement
        // call, not an oversight.
        if (body instanceof PendingAware pendingAware && pendingAware.pending()) {
            return ApiResponse.pending(msgId, correlationId, body, now);
        }
        return ApiResponse.success(msgId, correlationId, body, now);
    }
}
