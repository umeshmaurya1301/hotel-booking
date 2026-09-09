package com.umesh.hotelbooking.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Encrypts a {@code String} column on the way to the database and decrypts it on the way back
 * (design doc 12.6, Phase 10). Applied per field with {@code @Convert}, never globally with
 * {@code autoApply}: encrypting every string in the schema would put ciphertext in
 * {@code booking_uid}, {@code city_normalised} and every other column something actually
 * queries or indexes by value.
 *
 * <p><b>On the injection.</b> Hibernate, not Spring, instantiates {@link AttributeConverter}s
 * by default — a converter needing a collaborator is the standard place this breaks. It works
 * here because Spring Boot hands Hibernate a {@code SpringBeanContainer}, which resolves this
 * converter from the application context (declared in {@code FieldEncryptionConfig}) with its
 * {@link FieldCipher} already injected. That is a real dependency on Boot's autoconfiguration
 * rather than a property of JPA, which is why {@code GuestEncryptionAtRestTest} asserts
 * round-tripping through a real persistence context rather than testing {@link FieldCipher}
 * alone: a unit test of the cipher would still pass if this wiring silently fell back to a
 * no-arg instance.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final FieldCipher cipher;

    public EncryptedStringConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
