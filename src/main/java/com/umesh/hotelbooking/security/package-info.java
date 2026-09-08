/**
 * Data-protection mechanics (design doc 12.6): the {@code @Sensitive} annotation and its
 * Jackson serializer for structural masking, the shared {@link
 * com.umesh.hotelbooking.security.Masker} rules both that serializer and payload redaction
 * call, and {@link com.umesh.hotelbooking.security.PayloadRedactor} /
 * {@link com.umesh.hotelbooking.security.LogRedactionConverter} for provider payloads and
 * log lines respectively.
 */
package com.umesh.hotelbooking.security;
