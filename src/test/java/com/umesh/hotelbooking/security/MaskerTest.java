package com.umesh.hotelbooking.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins every masking rule in design doc 12.6.4's table, plus the null/empty/garbage edge
 * cases the class Javadoc promises never throw.
 */
class MaskerTest {

    @Test
    void panMasksFirstSixLastFour() {
        assertThat(Masker.pan("4111111111111111")).isEqualTo("411111XXXXXX1111");
    }

    @Test
    void panUnderElevenDigitsFallsBackToFull() {
        assertThat(Masker.pan("1234567890")).isEqualTo("***");
    }

    @Test
    void last4KeepsAFixedFourStarPrefix() {
        assertThat(Masker.last4("4111111111111111")).isEqualTo("****1111");
    }

    @Test
    void emailMasksLocalPartKeepingDomain() {
        assertThat(Masker.email("asha@example.com")).isEqualTo("a***@example.com");
    }

    @Test
    void emailWithNoAtSignFallsBackToFull() {
        assertThat(Masker.email("not-an-email")).isEqualTo("***");
    }

    @Test
    void phoneKeepsLastFourDigitsBehindAFiveStarPrefix() {
        assertThat(Masker.phone("+919876543210")).isEqualTo("*****3210");
    }

    @Test
    void nameMasksEachTokenToItsFirstLetter() {
        assertThat(Masker.name("Asha Menon")).isEqualTo("A*** M****");
    }

    @Test
    void nameWithASingleLetterTokenKeepsJustTheLetter() {
        assertThat(Masker.name("A B")).isEqualTo("A B");
    }

    @Test
    void fullAlwaysMasksToThreeStars() {
        assertThat(Masker.full("anything at all")).isEqualTo("***");
    }

    @Test
    void nullInputMasksToNullForEveryMode() {
        for (Masking mode : Masking.values()) {
            assertThat(Masker.mask(mode, null)).as("mode %s", mode).isNull();
        }
    }

    @Test
    void emptyStringMasksToThreeStarsNotAnEmptyString() {
        for (Masking mode : Masking.values()) {
            assertThat(Masker.mask(mode, "")).as("mode %s", mode).isEqualTo("***");
        }
    }

    @Test
    void garbageAndShortInputsNeverThrow() {
        String[] garbage = {"x", "12", "@", " ", "----", "a@", "@b"};
        for (Masking mode : Masking.values()) {
            for (String input : garbage) {
                assertThatCode(() -> Masker.mask(mode, input))
                        .as("mode %s input %s", mode, input)
                        .doesNotThrowAnyException();
            }
        }
    }
}
