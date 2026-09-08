package com.umesh.hotelbooking.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code @Sensitive} masks through the <em>application's</em> {@link ObjectMapper} —
 * the same bean {@code IdempotencyService} injects — not a hand-rolled one built just for this
 * test. A masked API response and an unmasked stored replay is exactly the leak design doc
 * 12.6.4 exists to prevent, and it would be invisible if this test used its own mapper.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SensitiveSerializerTest {

    private record Probe(@Sensitive(Masking.EMAIL) String email, String plain) {
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void aSensitiveFieldSerialisesMaskedThroughTheApplicationObjectMapper() {
        String json = objectMapper.writeValueAsString(new Probe("asha@example.com", "unchanged"));

        assertThat(json).contains("\"email\":\"a***@example.com\"");
        assertThat(json).doesNotContain("asha@example.com");
    }

    @Test
    void anUnannotatedFieldIsSerialisedAsIs() {
        String json = objectMapper.writeValueAsString(new Probe("asha@example.com", "unchanged"));

        assertThat(json).contains("\"plain\":\"unchanged\"");
    }

    @Test
    void aNullSensitiveFieldSerialisesAsNull() {
        String json = objectMapper.writeValueAsString(new Probe(null, "unchanged"));

        assertThat(json).contains("\"email\":null");
    }
}
