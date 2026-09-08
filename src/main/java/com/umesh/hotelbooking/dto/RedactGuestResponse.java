package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Guest;

import java.time.Instant;

/**
 * Confirms an erasure request (design doc 12.6.3, POST /api/v1/admin/guests/{id}/redact).
 * Deliberately carries no personal fields at all — by the time this response exists they are
 * already tombstoned, so there is nothing left worth a {@code @Sensitive} field to protect.
 */
public record RedactGuestResponse(String guestUid, Instant redactedAt) {

    public static RedactGuestResponse from(Guest guest) {
        return new RedactGuestResponse(guest.getGuestUid(), guest.getRedactedAt());
    }
}
