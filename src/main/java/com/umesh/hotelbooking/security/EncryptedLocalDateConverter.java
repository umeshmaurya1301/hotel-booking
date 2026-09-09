package com.umesh.hotelbooking.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * The {@link EncryptedStringConverter} equivalent for {@code Guest.dateOfBirth} (design doc
 * 12.6, Phase 10).
 *
 * <p>A date of birth is at least as identifying as a phone number, so leaving it as the one
 * plaintext PII column while encrypting the rest would be an odd line to draw. The cost is
 * that the column stops being a {@code DATE} and becomes a string of ciphertext: it can no
 * longer be range-queried or compared in SQL. Nothing does either — no repository method, no
 * report, and no service reads it for anything but returning it to an admin lookup — so the
 * capability being given up is one this system never used. That is the whole reason this is
 * defensible here and would not be on {@code Booking.checkIn}, which is queried by range on
 * the hottest path in the system.
 *
 * <p>Stored in ISO-8601 form before encryption, so a decrypted value is unambiguous
 * independently of any locale or JDBC driver date handling.
 */
@Converter
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final FieldCipher cipher;

    public EncryptedLocalDateConverter(FieldCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : cipher.encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        String decrypted = cipher.decrypt(dbData);
        if (decrypted == null || decrypted.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(decrypted);
        } catch (DateTimeParseException e) {
            // Never echo the value: a failed parse here is still personal data.
            throw new IllegalStateException("Stored date of birth is not a valid ISO-8601 date", e);
        }
    }
}
