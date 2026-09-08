package com.umesh.hotelbooking.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HmacSignerTest {

    private final HmacSigner signer = new HmacSigner();

    @Test
    void signThenVerifyRoundTrips() {
        byte[] body = "{\"eventId\":\"evt-1\"}".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(body, "secret");

        assertThat(signer.verify(body, "secret", signature)).isTrue();
    }

    @Test
    void aOneByteBodyChangeFailsVerification() {
        byte[] original = "{\"amount\":100}".getBytes(StandardCharsets.UTF_8);
        byte[] tampered = "{\"amount\":900}".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(original, "secret");

        assertThat(signer.verify(tampered, "secret", signature)).isFalse();
    }

    @Test
    void aDifferentSecretFailsVerification() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(body, "secret-a");

        assertThat(signer.verify(body, "secret-b", signature)).isFalse();
    }

    @Test
    void aTruncatedSignatureReturnsFalseRatherThanThrowing() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(body, "secret");
        String truncated = signature.substring(0, signature.length() - 10);

        assertThatCode(() -> assertThat(signer.verify(body, "secret", truncated)).isFalse())
                .doesNotThrowAnyException();
    }

    /** Only the hex digits change case here — the {@code sha256=} prefix is a fixed protocol
     * literal, not something a real provider would send uppercased. */
    @Test
    void aDifferentHexCaseStillVerifies() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = signer.sign(body, "secret");
        String upperHex = "sha256=" + signature.substring("sha256=".length()).toUpperCase();

        assertThat(signer.verify(body, "secret", upperHex)).isTrue();
    }

    @Test
    void aNonHexSignatureReturnsFalseRatherThanThrowing() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> assertThat(signer.verify(body, "secret", "sha256=not-hex-at-all!!")).isFalse())
                .doesNotThrowAnyException();
    }

    @Test
    void aNullPresentedSignatureReturnsFalse() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

        assertThat(signer.verify(body, "secret", null)).isFalse();
    }

    @Test
    void signedValueCarriesTheSha256Prefix() {
        String signature = signer.sign("x".getBytes(StandardCharsets.UTF_8), "secret");

        assertThat(signature).startsWith("sha256=");
    }
}
