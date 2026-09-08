package com.umesh.hotelbooking.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Design doc 12.6.2's distinction, exercised directly: sensitive authentication data is
 * dropped, PAN-shaped data is masked, and both survive nesting.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PayloadRedactorTest {

    @Autowired
    private PayloadRedactor redactor;

    @Test
    void cvvIsDroppedNotMasked() {
        String redacted = redactor.redact("""
                {"pan":"4111111111111111","cvv":"123"}""");

        assertThat(redacted).doesNotContain("cvv").doesNotContain("123");
    }

    @Test
    void pinAndTrackDataAreDropped() {
        String redacted = redactor.redact("""
                {"pin":"9999","pinBlock":"ABCDEF","track1":"stuff","track2":"more"}""");

        assertThat(redacted).isEqualTo("{}");
    }

    @Test
    void panIsMaskedFirstSixLastFour() {
        String redacted = redactor.redact("""
                {"pan":"4111111111111111"}""");

        assertThat(redacted).contains("411111XXXXXX1111");
        assertThat(redacted).doesNotContain("4111111111111111");
    }

    @Test
    void nestedObjectsAreReached() {
        String redacted = redactor.redact("""
                {"instrument":{"pan":"4111111111111111","cvv":"123"}}""");

        assertThat(redacted).contains("411111XXXXXX1111");
        assertThat(redacted).doesNotContain("cvv").doesNotContain("\"123\"");
    }

    @Test
    void nestedArraysAreReached() {
        String redacted = redactor.redact("""
                {"instruments":[{"pan":"4111111111111111"},{"cvv":"123"}]}""");

        assertThat(redacted).contains("411111XXXXXX1111");
        assertThat(redacted).doesNotContain("cvv");
    }

    @Test
    void keyMatchingIsCaseAndSeparatorInsensitive() {
        assertThat(redactor.redact("""
                {"card_number":"4111111111111111"}""")).contains("411111XXXXXX1111");
        assertThat(redactor.redact("""
                {"cardNumber":"4111111111111111"}""")).contains("411111XXXXXX1111");
        assertThat(redactor.redact("""
                {"CardNumber":"4111111111111111"}""")).contains("411111XXXXXX1111");
    }

    @Test
    void unparseableInputReturnsThreeStars() {
        assertThat(redactor.redact("not { valid json")).isEqualTo("***");
    }

    @Test
    void contactAndNameFieldsAreMasked() {
        String redacted = redactor.redact("""
                {"email":"asha@example.com","phone":"+919876543210","vpa":"asha@upi",\
                "billingName":"Asha Menon"}""");

        assertThat(redacted).doesNotContain("asha@example.com", "+919876543210", "asha@upi", "Asha Menon");
    }
}
