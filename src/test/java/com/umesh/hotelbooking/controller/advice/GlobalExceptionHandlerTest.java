package com.umesh.hotelbooking.controller.advice;

import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.controller.user.ProbeController;
import com.umesh.hotelbooking.config.ClockConfig;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.CorrelationIdFilter;
import com.umesh.hotelbooking.web.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link GlobalExceptionHandler} maps every exception that escapes a controller to a full
 * {@code ApiResponse} carrying a {@code FAILURE} status and an {@code ApiError}.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aDomainExceptionMapsToItsErrorCodeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/test/domain-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.error.code").value("BOOKING_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * Validating {@code ApiRequest<T>.payload} reports the field path prefixed with {@code
     * payload.} — that is where the field actually lives once every request is enveloped, not
     * a cosmetic wart to strip (design doc §6.2 of this phase's task spec).
     */
    @Test
    void aBlankRequiredInnerFieldIsRejectedWithThePayloadPrefixedPath() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "msgId": "msg-1",
                                  "timestamp": "2026-09-08T10:00:00Z",
                                  "channel": "WEB",
                                  "version": "v1",
                                  "payload": {"requiredField": ""}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("payload.requiredField"));
    }

    /**
     * The catch-all is a security boundary: the response message must never be the raw
     * exception message, since that routinely holds something like a connection string.
     */
    @Test
    void anUnmappedExceptionBecomesAGenericInternalError() throws Exception {
        mockMvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILURE"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret"))));
    }
}
