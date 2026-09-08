package com.umesh.hotelbooking.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Stamps {@link ApiContext#getApiType()} from the {@link Api} annotation on the matched
 * handler method (design doc 11.3) — server-derived from the route, never client-supplied.
 */
public class ApiTypeInterceptor implements HandlerInterceptor {

    private final ApiContext apiContext;

    public ApiTypeInterceptor(ApiContext apiContext) {
        this.apiContext = apiContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            Api api = handlerMethod.getMethodAnnotation(Api.class);
            apiContext.setApiType(api != null ? api.value() : ApiType.UNKNOWN);
        }
        return true;
    }
}
