package com.umesh.hotelbooking.controller;

import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.controller.advice.GlobalExceptionHandler;
import com.umesh.hotelbooking.controller.advice.RequestEnvelopeAdvice;
import com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice;
import com.umesh.hotelbooking.config.ClockConfig;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.CorrelationIdFilter;
import com.umesh.hotelbooking.web.WebConfig;
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
 * {@link com.umesh.hotelbooking.dto.PendingAware} is what turns an unresolved payment-shaped
 * response into a {@code PENDING} envelope (design doc 7.2, 11.2) — pending is not an error,
 * so it must still be HTTP 200 with a populated {@code data}.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class PendingResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aPendingAwareResponseRendersAsPendingWithHttp200() throws Exception {
        mockMvc.perform(get("/api/v1/test/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.data.flag").value(true))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void aNonPendingAwareOutcomeRendersAsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    /**
     * A list of pending-aware items was itself retrieved successfully — only a single
     * pending payload renders as PENDING, not a collection containing one. Deliberate, per
     * {@code ResponseEnvelopeAdvice}'s own Javadoc.
     */
    @Test
    void aListOfPendingItemsStillRendersAsSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/test/pending-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].flag").value(true));
    }
}
