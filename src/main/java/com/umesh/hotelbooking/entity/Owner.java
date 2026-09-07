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
 * The account at the top of the ownership hierarchy (design doc 3.1):
 * Owner -> PropertyGroup -> Property -> RoomType -> DailyInventory.
 *
 * <p>An owner is an operator account, not a guest, so the erasure machinery of 12.6.3 does
 * not apply to it. It still carries contact details, so no Lombok {@code @ToString} here.
 */
@Entity
@Table(name = "owners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String ownerUid;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 320)
    private String email;

    @PrePersist
    private void onCreate() {
        if (ownerUid == null) {
            ownerUid = UUID.randomUUID().toString();
        }
    }
}
