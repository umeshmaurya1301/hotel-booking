package com.umesh.hotelbooking.security;

import com.umesh.hotelbooking.config.EncryptionProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-level properties of the AES-GCM field cipher (design doc 12.6, Phase 10). The
 * persistence half — that the converters are actually wired into Hibernate and that what
 * lands in the column is genuinely unreadable — is {@code GuestEncryptionAtRestTest}, because
 * a passing test here proves nothing about whether the converter was ever invoked.
 */
class FieldCipherTest {

    private static final String KEY_256 = "b64He55mmu7jU8z2XNEuO4+5xg5ssKEr7bEZ3xVDUZg=";

    private FieldCipher cipher() {
        return new FieldCipher(new EncryptionProperties(KEY_256));
    }

    @Test
    void aValueRoundTripsBackToItself() {
        FieldCipher cipher = cipher();
        String plaintext = "Asha Menon";

        String encrypted = cipher.encrypt(plaintext);

        assertThat(encrypted).doesNotContain(plaintext);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void multiByteCharactersSurviveTheRoundTrip() {
        FieldCipher cipher = cipher();
        String plaintext = "आशा मेनन, बेंगलुरु — ☕";

        assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
    }

    /**
     * The property a random IV buys, and the reason ECB would be wrong here: two guests with
     * the same name must not produce the same ciphertext, or the column leaks equality even
     * while hiding values.
     */
    @Test
    void theSamePlaintextEncryptsDifferentlyEveryTime() {
        FieldCipher cipher = cipher();

        String first = cipher.encrypt("Asha Menon");
        String second = cipher.encrypt("Asha Menon");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo(cipher.decrypt(second)).isEqualTo("Asha Menon");
    }

    /** GCM is authenticated: a tampered value must fail loudly, never decrypt to plausible junk. */
    @Test
    void aTamperedCiphertextIsRejectedRatherThanSilentlyDecrypted() {
        FieldCipher cipher = cipher();
        String encrypted = cipher.encrypt("Asha Menon");

        char lastChar = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (lastChar == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> cipher.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not decryptable");
    }

    @Test
    void aValueEncryptedUnderADifferentKeyDoesNotDecrypt() {
        String encrypted = cipher().encrypt("Asha Menon");
        FieldCipher otherKey = new FieldCipher(
                new EncryptionProperties("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));

        assertThatThrownBy(() -> otherKey.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullStaysNullSoAnAbsentFieldDoesNotBecomeCiphertextMeaningNothing() {
        FieldCipher cipher = cipher();

        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    /**
     * The rollout affordance: a row written before this class existed carries no version
     * prefix and must still read, or switching encryption on would take the application down
     * against an existing database.
     */
    @Test
    void aValueWithoutTheVersionPrefixIsTreatedAsPreEncryptionPlaintext() {
        assertThat(cipher().decrypt("Asha Menon")).isEqualTo("Asha Menon");
    }

    @Test
    void aMissingOrMalformedKeyFailsAtStartupRatherThanAtTheFirstWrite() {
        assertThatThrownBy(() -> new FieldCipher(new EncryptionProperties(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not set");

        assertThatThrownBy(() -> new FieldCipher(new EncryptionProperties("not-base64!!")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");

        // Valid Base64, wrong length for AES.
        assertThatThrownBy(() -> new FieldCipher(new EncryptionProperties("dG9vLXNob3J0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES requires");
    }
}
