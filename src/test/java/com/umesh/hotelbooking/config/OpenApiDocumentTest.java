package com.umesh.hotelbooking.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The guard on Phase 10's actual deliverable. Adding springdoc is one line in
 * {@code build.gradle.kts}; making the document it generates <em>true</em> is
 * {@link OpenApiConfig}, and nothing else in this suite would notice if that stopped working.
 *
 * <p>What would go wrong without this test is specific, not hypothetical: out of the box
 * springdoc documented {@code POST /api/v1/user/bookings} as a 200 returning a bare {@code
 * BookingResponse}, when it is really a 201 returning that response nested under {@code data}
 * inside an {@code ApiResponse} envelope. A client generated from that document looks for
 * {@code bookingUid} at the top level and never finds it. The assertions below are the ones
 * that fail if {@code OpenApiConfig}'s customizers stop being applied, or if a future
 * controller package is enveloped by {@code ResponseEnvelopeAdvice} without being added to
 * {@code OpenApiConfig}'s own list.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "inventory.horizon-days=30",
        "booking.sweeper.enabled=false",
        "payment.reconciliation.enabled=false"
})
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private static JsonNode document;

    @BeforeAll
    static void resetCache() {
        document = null;
    }

    private JsonNode document() throws Exception {
        if (document == null) {
            String body = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            document = objectMapper.readTree(body);
        }
        return document;
    }

    private JsonNode responseSchema(String path, String method, String statusCode) throws Exception {
        JsonNode content = document().path("paths").path(path).path(method)
                .path("responses").path(statusCode).path("content");
        assertThat(content.isMissingNode() || content.isEmpty())
                .as("%s %s has no documented %s response at all", method, path, statusCode)
                .isFalse();
        String firstMediaType = content.propertyNames().iterator().next();
        return content.path(firstMediaType).path("schema");
    }

    @Test
    void everyEndpointIsDocumented() throws Exception {
        JsonNode paths = document().path("paths");
        assertThat(paths.has("/api/v1/user/bookings")).isTrue();
        assertThat(paths.has("/api/v1/user/properties/search")).isTrue();
        assertThat(paths.has("/api/v1/admin/properties")).isTrue();
        assertThat(paths.has("/api/v1/webhooks/payment/{providerCode}")).isTrue();
    }

    @Test
    void aUserEndpointsSuccessResponseIsDocumentedAsTheEnvelopeNotTheBarePayload() throws Exception {
        JsonNode schema = responseSchema("/api/v1/user/bookings", "post", "201");

        JsonNode allOf = schema.path("allOf");
        assertThat(allOf.isArray()).as("an enveloped response is an allOf composition").isTrue();
        assertThat(allOf.get(0).path("$ref").asString())
                .isEqualTo("#/components/schemas/" + OpenApiConfig.ENVELOPE_SCHEMA);
        assertThat(allOf.get(1).path("properties").path("data").path("$ref").asString())
                .as("the real payload is nested under data, never at the top level")
                .isEqualTo("#/components/schemas/BookingResponse");
    }

    /**
     * The status code is the half of this that a schema assertion alone would miss: it was
     * documented as 200 while the endpoint genuinely returned 201, because the status was set
     * inside a {@code ResponseEntity} rather than declared where anything could read it.
     */
    @Test
    void aCreatedEndpointIsDocumentedAs201NotAsAPlain200() throws Exception {
        JsonNode bookingResponses = document().path("paths").path("/api/v1/user/bookings")
                .path("post").path("responses");
        assertThat(bookingResponses.has("201")).isTrue();
        assertThat(bookingResponses.has("200")).as("200 would be the wrong status to advertise").isFalse();

        JsonNode onboardResponses = document().path("paths").path("/api/v1/admin/properties")
                .path("post").path("responses");
        assertThat(onboardResponses.has("201")).isTrue();
        assertThat(onboardResponses.has("200")).isFalse();
    }

    /**
     * The webhook path is genuinely not enveloped — {@code ResponseEnvelopeAdvice} is scoped
     * away from it — so documenting it as enveloped would be just as wrong as the reverse.
     */
    @Test
    void theWebhookResponseIsDocumentedUnwrappedBecauseItGenuinelyIs() throws Exception {
        JsonNode schema = responseSchema("/api/v1/webhooks/payment/{providerCode}", "post", "200");

        assertThat(schema.has("allOf")).as("the webhook contract is the provider's, not ours").isFalse();
        assertThat(schema.path("$ref").asString()).isEqualTo("#/components/schemas/WebhookAck");
    }

    @Test
    void theEnvelopeAndItsErrorPayloadAreBothRegisteredAsComponents() throws Exception {
        JsonNode schemas = document().path("components").path("schemas");

        JsonNode envelope = schemas.path(OpenApiConfig.ENVELOPE_SCHEMA);
        assertThat(envelope.isMissingNode()).isFalse();
        assertThat(envelope.path("properties").propertyNames())
                .contains("msgId", "correlationId", "status", "error", "respondedAt");
        assertThat(envelope.path("properties").path("status").path("enum").toString())
                .contains("SUCCESS", "FAILURE", "PENDING");

        // ApiError is never a controller return type, so springdoc has no reason to find it on
        // its own — but every FAILURE response carries one.
        assertThat(schemas.path("ApiError").isMissingNode())
                .as("the error payload must be documented even though no method returns it")
                .isFalse();
    }

    @Test
    void theStubbedRoleHeaderIsDocumentedOnRoleGuardedRoutesAndNotOnTheWebhook() throws Exception {
        assertThat(headerNames("/api/v1/user/bookings", "post")).contains("X-Role");
        assertThat(headerNames("/api/v1/admin/properties", "post")).contains("X-Role");
        assertThat(headerNames("/api/v1/webhooks/payment/{providerCode}", "post"))
                .as("webhooks authenticate by HMAC signature, not the role header")
                .doesNotContain("X-Role")
                .contains("X-Signature", "X-Timestamp");
    }

    private java.util.List<String> headerNames(String path, String method) throws Exception {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (JsonNode parameter : document().path("paths").path(path).path(method).path("parameters")) {
            if ("header".equals(parameter.path("in").asString())) {
                names.add(parameter.path("name").asString());
            }
        }
        return names;
    }
}
