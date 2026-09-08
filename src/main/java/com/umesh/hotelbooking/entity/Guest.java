package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
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

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 300)
    private String address;

    @Column(name = "date_of_birth")
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
