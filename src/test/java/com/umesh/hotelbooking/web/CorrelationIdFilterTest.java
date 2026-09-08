package com.umesh.hotelbooking.web;

import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.config.ClockConfig;
import com.umesh.hotelbooking.controller.user.ProbeController;
import com.umesh.hotelbooking.controller.advice.GlobalExceptionHandler;
import com.umesh.hotelbooking.controller.advice.RequestEnvelopeAdvice;
import com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CorrelationIdFilter} is the server's own trace handle (design doc 11.2): always
 * generated, always returned, and never taken from the client.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class CorrelationIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void everyResponseCarriesACorrelationIdHeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void twoRequestsReceiveDifferentCorrelationIds() throws Exception {
        String first = mockMvc.perform(get("/api/v1/test/success")).andReturn().getResponse().getHeader("X-Correlation-Id");
        String second = mockMvc.perform(get("/api/v1/test/success")).andReturn().getResponse().getHeader("X-Correlation-Id");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aClientSuppliedCorrelationIdHeaderIsIgnoredNotEchoed() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/test/success").header("X-Correlation-Id", "client-supplied-value"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Correlation-Id"))
                .isNotBlank()
                .isNotEqualTo("client-supplied-value");
    }
}
