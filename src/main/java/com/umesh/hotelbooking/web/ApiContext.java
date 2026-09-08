package com.umesh.hotelbooking.web;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Per-request state assembled across the filter and interceptor chain: the server-generated
 * {@code correlationId}, the client's {@code msgId} (once the body has been read), and the
 * {@link ApiType} derived from the matched route.
 *
 * <p>{@code @RequestScope} defaults to {@code ScopedProxyMode.TARGET_CLASS}, so this injects
 * cleanly into the singleton filter, interceptors, advices and exception handler with no extra
 * configuration.
 *
 * <p>Deliberately confined to {@code web}, {@code controller} and {@code controller.advice}.
 * Never inject this into {@code service}, {@code gateway} or any {@code @Scheduled} component:
 * those beans are also invoked with no HTTP request in flight (the reconciliation and sweeper
 * schedulers), where a request-scoped proxy has nothing to resolve against. Business services
 * that need {@code msgId} or a correlation id take them as explicit method parameters instead
 * — see {@link RequestMeta} — which keeps the service layer trivially unit-testable with a
 * literal value.
 */
@Component
@RequestScope
public class ApiContext {

    private String correlationId;
    private String msgId;
    private ApiType apiType = ApiType.UNKNOWN;

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getMsgId() {
        return msgId;
    }

    /**
     * Set by {@code RequestEnvelopeAdvice} once the envelope has been deserialized — public
     * rather than package-private because that advice lives in {@code controller.advice}, not
     * {@code web}, so a validation failure can still echo {@code msgId} in the error response.
     */
    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public ApiType getApiType() {
        return apiType;
    }

    public void setApiType(ApiType apiType) {
        this.apiType = apiType;
    }

    /** Snapshots the current request's identifiers for passing into the service layer. */
    public RequestMeta toRequestMeta() {
        return new RequestMeta(msgId, apiType, correlationId);
    }
}
