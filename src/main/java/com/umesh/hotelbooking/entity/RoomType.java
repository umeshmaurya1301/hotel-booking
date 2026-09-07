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
 * Placeholder for the RoomType aggregate — enough to give {@code RoomTypeRepository} a
 * concrete target. Capacity, amenities and base pricing are built in the onboarding phase.
 */
@Entity
@Table(name = "room_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_type_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String roomTypeUid;

    @PrePersist
    private void onCreate() {
        if (roomTypeUid == null) {
            roomTypeUid = UUID.randomUUID().toString();
        }
    }
}
