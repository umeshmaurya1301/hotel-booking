package com.umesh.hotelbooking.web;

import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.config.ClockConfig;
import com.umesh.hotelbooking.controller.ProbeController;
import com.umesh.hotelbooking.controller.advice.GlobalExceptionHandler;
import com.umesh.hotelbooking.controller.advice.RequestEnvelopeAdvice;
import com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link RoleInterceptor}'s trivially-stubbed enforcement of {@link RequireRole} (design doc
 * 11.4): a mismatched {@code X-Role} is rejected, a matching one passes, and — the whole point
 * of the stub — an absent header is let through rather than treated as denied.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class RoleInterceptorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aMismatchedRoleHeaderIsRejectedWithForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only").header("X-Role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void aMatchingRoleHeaderIsLetThrough() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only").header("X-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("ok"));
    }

    /** The point of the stub: authorisation is out of scope, so no header must not break callers. */
    @Test
    void aMissingRoleHeaderIsLetThrough() throws Exception {
        mockMvc.perform(get("/api/v1/test/admin-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value").value("ok"));
    }
}
