package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.security.Masking;
import com.umesh.hotelbooking.security.Masker;
import com.umesh.hotelbooking.security.Sensitive;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Optional guest profile fields supplied at booking time (design doc 12.6.1, 12.6.4). Every
 * field is optional — a first-time booker who supplies nothing still gets a booking; supplying
 * these simply populates the {@code Guest} row this booking resolves to instead of leaving it
 * blank.
 *
 * <p>Every field carries {@code @Sensitive}, so a stray {@code log.info("{}", request)} or an
 * accidental field addition to a response DTO masks by default (design doc 12.6.4). The
 * hand-written {@link #toString()} is the same discipline applied where Jackson never runs at
 * all — never rely on a record's generated {@code toString()} for a type carrying personal
 * data.
 */
public record GuestDetails(
        @Sensitive(Masking.NAME) @Size(max = 120) String fullName,
        @Sensitive(Masking.EMAIL) @Email @Size(max = 200) String email,
        @Sensitive(Masking.PHONE) @Size(max = 20) String phone,
        @Sensitive @Size(max = 300) String address,
        @Sensitive LocalDate dateOfBirth) {

    @Override
    public String toString() {
        return "GuestDetails[fullName=" + Masker.mask(Masking.NAME, fullName)
                + ", email=" + Masker.mask(Masking.EMAIL, email)
                + ", phone=" + Masker.mask(Masking.PHONE, phone)
                + ", address=" + Masker.mask(Masking.FULL, address)
                + ", dateOfBirth=" + Masker.mask(Masking.FULL, dateOfBirth == null ? null : dateOfBirth.toString())
                + "]";
    }
}
