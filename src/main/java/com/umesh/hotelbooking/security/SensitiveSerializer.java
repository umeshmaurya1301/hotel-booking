package com.umesh.hotelbooking.security;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Masks whatever value it is asked to serialise, using the {@link Masking} mode declared on
 * the {@link Sensitive}-annotated property it was resolved for (design doc 12.6.4).
 *
 * <p>Registered per-property via {@code @JsonSerialize(using = SensitiveSerializer.class)}
 * inside {@link Sensitive} rather than as a global {@code ValueSerializerModifier} module —
 * the annotation route needs no separate {@code Module} bean and resolves the masking mode
 * through {@link #createContextual}, which is the Jackson 3 equivalent of Jackson 2's
 * {@code ContextualSerializer} (folded directly into {@link ValueSerializer} in Jackson 3,
 * not a separate interface).
 *
 * <p>The no-arg constructor Jackson instantiates from the annotation always masks {@link
 * Masking#FULL} until {@link #createContextual} swaps in a serializer configured with the
 * annotation's actual {@code value()} — masking fully by default is the fail-closed choice if
 * that resolution is ever skipped.
 */
public class SensitiveSerializer extends ValueSerializer<Object> {

    private final Masking masking;

    public SensitiveSerializer() {
        this(Masking.FULL);
    }

    private SensitiveSerializer(Masking masking) {
        this.masking = masking;
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(Masker.mask(masking, value.toString()));
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return this;
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            return this;
        }
        return new SensitiveSerializer(annotation.value());
    }
}
