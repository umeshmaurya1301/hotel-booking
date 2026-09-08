package com.umesh.hotelbooking.web;

import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Trivially-stubbed enforcement of {@link RequireRole} (design doc 11.4, final paragraph).
 *
 * <p>Reads the {@code X-Role} request header and compares it against the annotation on the
 * handler method, falling back to the declaring class. A mismatch is rejected with {@link
 * ForbiddenException}. <b>A missing header is allowed through</b> — this is the whole point:
 * authorisation is out of scope per the brief, so the default must not break the demo seed
 * flow, the README's curl examples, or any existing test. This interceptor demonstrates that
 * role separation was designed for; it does not pretend to be security. There is deliberately
 * no Spring Security, no token, no user store behind it.
 */
public class RoleInterceptor implements HandlerInterceptor {

    private final ApiProperties apiProperties;

    public RoleInterceptor(ApiProperties apiProperties) {
        this.apiProperties = apiProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        String callerRole = request.getHeader(apiProperties.roleHeader());
        if (callerRole == null || callerRole.isBlank()) {
            return true;
        }
        if (!requireRole.value().name().equals(callerRole)) {
            throw new ForbiddenException(
                    "Route requires role " + requireRole.value() + " but caller declared " + callerRole);
        }
        return true;
    }
}
