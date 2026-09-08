package com.umesh.hotelbooking.web;

import com.umesh.hotelbooking.config.ApiProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.RequestContextFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the API-layer interceptors against every {@code /api/v1/**} route.
 *
 * <p>Order matters: {@link ApiTypeInterceptor} runs before {@link RoleInterceptor} so the role
 * check can see the api type on {@link ApiContext} if it ever needs to (design doc 11.3/11.4).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String API_PATTERN = "/api/v1/**";

    private final ApiContext apiContext;
    private final ApiProperties apiProperties;

    public WebConfig(ApiContext apiContext, ApiProperties apiProperties) {
        this.apiContext = apiContext;
        this.apiProperties = apiProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiTypeInterceptor(apiContext)).addPathPatterns(API_PATTERN);
        registry.addInterceptor(new RoleInterceptor(apiProperties)).addPathPatterns(API_PATTERN);
    }

    /**
     * Binds {@code RequestContextHolder} before any other filter runs, so a {@code
     * @RequestScope} bean (namely {@link ApiContext}) can be resolved from within {@link
     * CorrelationIdFilter} — a plain servlet {@code Filter} runs before {@code
     * DispatcherServlet} ever gets a chance to bind the request itself. Without this, every
     * request outside of a MockMvc-driven test (whose own test-context scaffolding hides the
     * gap) throws {@code ScopeNotActiveException} the moment the filter touches the bean.
     */
    @Bean
    public FilterRegistrationBean<RequestContextFilter> requestContextFilter() {
        FilterRegistrationBean<RequestContextFilter> registration =
                new FilterRegistrationBean<>(new RequestContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
