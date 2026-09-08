package com.umesh.hotelbooking.web;

import com.umesh.hotelbooking.config.ApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates the server-side trace handle for every request, as early as possible in the
 * filter chain.
 *
 * <p>Any inbound correlation-id header is deliberately ignored, not echoed (design doc 11.2):
 * {@code correlationId} is the server's own trace handle, distinct in purpose from the
 * client-supplied {@code msgId}. Accepting a client-supplied value would let one client
 * collide two unrelated traces onto the same id.
 *
 * <p>Ordered just <em>after</em> {@code Ordered.HIGHEST_PRECEDENCE}, not at it: {@link
 * ApiContext} is {@code @RequestScope}, and resolving a request-scoped bean needs {@code
 * RequestContextHolder} already populated. That population is Spring's own {@code
 * RequestContextFilter}, registered at the true highest precedence in {@code WebConfig} — a
 * filter that runs before the servlet does, unlike {@code DispatcherServlet}'s own binding,
 * which only happens once dispatch has already reached it. Filters, unlike
 * {@code @RequestScope} beans, resolve in registration order regardless of Spring MVC, so this
 * one has to come after that one by construction, not by convention.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String MDC_KEY = "correlationId";

    private final ApiContext apiContext;
    private final ApiProperties apiProperties;

    public CorrelationIdFilter(ApiContext apiContext, ApiProperties apiProperties) {
        this.apiContext = apiContext;
        this.apiProperties = apiProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        apiContext.setCorrelationId(correlationId);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(apiProperties.correlationHeader(), correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // The container reuses threads across requests; a leaked MDC entry would mislabel
            // the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }
}
