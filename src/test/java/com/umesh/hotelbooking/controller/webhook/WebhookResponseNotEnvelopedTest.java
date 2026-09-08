package com.umesh.hotelbooking.controller.webhook;

import com.umesh.hotelbooking.config.ClockConfig;
import com.umesh.hotelbooking.config.ApiProperties;
import com.umesh.hotelbooking.controller.advice.GlobalExceptionHandler;
import com.umesh.hotelbooking.controller.advice.RequestEnvelopeAdvice;
import com.umesh.hotelbooking.controller.advice.ResponseEnvelopeAdvice;
import com.umesh.hotelbooking.controller.advice.WebhookExceptionHandler;
import com.umesh.hotelbooking.controller.user.ProbeController;
import com.umesh.hotelbooking.web.ApiContext;
import com.umesh.hotelbooking.web.CorrelationIdFilter;
import com.umesh.hotelbooking.web.WebConfig;
import com.umesh.hotelbooking.webhook.InboundWebhookService;
import com.umesh.hotelbooking.webhook.WebhookAck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Design doc task spec §11.1: a webhook response is the provider's own contract, not ours,
 * and must never be wrapped in {@code ApiResponse}. Proves the negative (webhook: no envelope
 * keys) and the positive (an ordinary endpoint in the same test run: still enveloped) side by
 * side, so a regression in either direction fails this one test.
 */
@WebMvcTest(controllers = {PaymentWebhookController.class, ProbeController.class})
@Import({ClockConfig.class, ApiContext.class, WebConfig.class, CorrelationIdFilter.class, RequestEnvelopeAdvice.class,
        ResponseEnvelopeAdvice.class, GlobalExceptionHandler.class, WebhookExceptionHandler.class})
@EnableConfigurationProperties(ApiProperties.class)
class WebhookResponseNotEnvelopedTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundWebhookService inboundWebhookService;

    @Test
    void aWebhookResponseHasNoEnvelopeKeys() throws Exception {
        when(inboundWebhookService.receive(anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(new WebhookAck("evt-1", "RECEIVED"));

        mockMvc.perform(post("/api/v1/webhooks/payment/MOCK_CARD")
                        .header("X-Signature", "sha256=doesnotmatter")
                        .header("X-Timestamp", "1700000000000")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.correlationId").doesNotExist())
                .andExpect(jsonPath("$.msgId").doesNotExist());
    }

    @Test
    void anOrdinaryEndpointInTheSameRunIsStillEnveloped() throws Exception {
        mockMvc.perform(get("/api/v1/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.value").value("ok"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }
}
