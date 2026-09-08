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

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A controller returning a plain DTO is wrapped in the full {@code ApiResponse} envelope
 * (design doc 11.2) by {@code ResponseEnvelopeAdvice}, without the controller ever building
 * one itself.
 */
@WebMvcTest(controllers = ProbeController.class)
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class ResponseEnvelopeAdviceTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aPlainDtoIsWrappedAsASuccessEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.value").value("ok"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    /** A GET has no request envelope in, so there is no msgId to echo. */
    @Test
    void aGetWithNoRequestBodyCarriesNoMsgIdToEcho() throws Exception {
        mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.msgId").doesNotExist());
    }

    @Test
    void aPostWithAnEnvelopeEchoesTheRequestsMsgId() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "msgId": "msg-echo-test",
                                  "timestamp": "2026-09-08T10:00:00Z",
                                  "channel": "WEB",
                                  "version": "v1",
                                  "payload": {"requiredField": "hello"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.msgId").value("msg-echo-test"))
                .andExpect(jsonPath("$.data.value").value("hello"));
    }
}
