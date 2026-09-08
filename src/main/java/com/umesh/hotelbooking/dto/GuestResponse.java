package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.security.Masking;
import com.umesh.hotelbooking.security.Masker;
import com.umesh.hotelbooking.security.Sensitive;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A guest profile as returned to an admin caller. Same masking discipline as {@link
 * GuestDetails} (design doc 12.6.4) — every personal field is {@code @Sensitive}, so it masks
 * by default wherever this DTO is serialised.
 */
public record GuestResponse(
        String guestUid,
        @Sensitive(Masking.NAME) String fullName,
        @Sensitive(Masking.EMAIL) String email,
        @Sensitive(Masking.PHONE) String phone,
        @Sensitive String address,
        @Sensitive LocalDate dateOfBirth,
        Instant redactedAt) {

    public static GuestResponse from(Guest guest) {
        return new GuestResponse(
                guest.getGuestUid(), guest.getFullName(), guest.getEmail(), guest.getPhone(),
                guest.getAddress(), guest.getDateOfBirth(), guest.getRedactedAt());
    }

    @Override
    public String toString() {
        return "GuestResponse[guestUid=" + guestUid
                + ", fullName=" + Masker.mask(Masking.NAME, fullName)
                + ", email=" + Masker.mask(Masking.EMAIL, email)
                + ", phone=" + Masker.mask(Masking.PHONE, phone)
                + ", address=" + Masker.mask(Masking.FULL, address)
                + ", dateOfBirth=" + Masker.mask(Masking.FULL, dateOfBirth == null ? null : dateOfBirth.toString())
                + ", redactedAt=" + redactedAt + "]";
    }
}
