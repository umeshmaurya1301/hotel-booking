package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.security.EncryptedLocalDateConverter;
import com.umesh.hotelbooking.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The guest profile (design doc 12.6.1). Referenced from {@link Booking} by {@code guestId}
 * only — personal data never inlines into an append-only record (design doc 12.6.3).
 *
 * <p>Every PII field is nullable: a first-time booker who supplies no {@code GuestDetails}
 * still gets a row with an id and nothing else, exactly as before this phase.
 *
 * <p><b>Every PII field is encrypted at rest</b> (Phase 10) via {@code EncryptedStringConverter}
 * / {@code EncryptedLocalDateConverter} — AES-GCM, so a stolen database file or backup does not
 * hand over names, emails, phones, addresses and dates of birth in the clear. Applied per field
 * rather than globally: encrypting columns something queries by value (every {@code *_uid},
 * {@code city_normalised}) would break those lookups outright.
 *
 * <p>The column lengths look arbitrary and are not. A field encrypted with a 12-byte IV and a
 * 16-byte GCM tag, then Base64-encoded with a version prefix, is roughly
 * {@code 4 * ceil((28 + utf8Bytes) / 3) + 3} characters — so each column is sized for its
 * {@code GuestDetails} counterpart's {@code @Size} limit at the UTF-8 worst case of four bytes
 * per character ({@code address}, capped at 300 characters on input, needs ~1640). The DTO's
 * validation limits are what keep these bounds honest: they cap the plaintext before it ever
 * reaches the converter.
 *
 * <p>Deliberately no unique constraint on {@code email}. Two bookings by the same person are
 * not a conflict, and a unique index on a redactable column would let an erasure tombstone
 * collide with another still-live row sharing the placeholder value.
 *
 * <p>{@code redactedAt} is the erasure tombstone marker (design doc 12.6.3): non-null means
 * this guest has been erased. Re-running erasure on an already-redacted guest is defined as a
 * no-op, not an error — see {@code GuestRedactionService}.
 *
 * <p><b>Deliberately no Lombok-generated string conversion.</b> An accidental {@code
 * log.info("{}", guest)} is the most common personal-data leak path there is (design doc
 * 12.6.4) — the omission is not an oversight to "complete" with the rest of the Lombok
 * annotation set. {@link #toString()} is hand-written below and reveals only the business id
 * and the erasure flag.
 */
@Entity
@Table(name = "guests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Guest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guest_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String guestUid;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "full_name", length = 1024)
    private String fullName;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 1536)
    private String email;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 256)
    private String phone;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(length = 2048)
    private String address;

    @Convert(converter = EncryptedLocalDateConverter.class)
    @Column(name = "date_of_birth", length = 128)
    private LocalDate dateOfBirth;

    @Column(name = "redacted_at")
    private Instant redactedAt;

    @PrePersist
    private void onCreate() {
        if (guestUid == null) {
            guestUid = UUID.randomUUID().toString();
        }
    }

    @Override
    public String toString() {
        return "Guest[guestUid=" + guestUid + ", redacted=" + (redactedAt != null) + "]";
    }
}
