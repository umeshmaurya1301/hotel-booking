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

import java.util.UUID;

/**
 * Placeholder for the Guest aggregate — enough to give {@code GuestRepository} a concrete
 * target. Guest profile data is built in a later phase and, per the design's PII policy, is
 * never referenced from {@link Booking} directly — only by {@code guestId}.
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

    @PrePersist
    private void onCreate() {
        if (guestUid == null) {
            guestUid = UUID.randomUUID().toString();
        }
    }
}
