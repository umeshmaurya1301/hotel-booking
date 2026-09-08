package com.umesh.hotelbooking.security;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field or record component as carrying personal or sensitive payment data (design
 * doc 12.6.4). {@link SensitiveSerializer} masks it wherever it is serialised — API
 * responses, {@code IdempotencyRecord.responseBody} (the same {@code ObjectMapper}), and
 * audit rows — so exposure requires an explicit act (dropping the annotation), never an
 * omission.
 *
 * <p>{@code @JacksonAnnotationsInside} bundles {@code @JsonSerialize} into this one
 * annotation, so a field only needs {@code @Sensitive}, not both. Jackson-annotations kept
 * its Jackson-2 package even under Jackson 3 (see {@code jackson-databind}'s own POM comment
 * to that effect), which is why this imports {@code com.fasterxml.jackson.annotation} here
 * while {@code JsonSerialize} — a databind-owned annotation — comes from {@code
 * tools.jackson.databind.annotation}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT})
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {
    Masking value() default Masking.FULL;
}
