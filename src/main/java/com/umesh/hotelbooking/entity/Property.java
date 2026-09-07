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
 * Placeholder for the Property aggregate — enough to give {@code PropertyRepository} a
 * concrete target. Name, location, star rating and room types are built in the onboarding
 * phase.
 */
@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String propertyUid;

    @PrePersist
    private void onCreate() {
        if (propertyUid == null) {
            propertyUid = UUID.randomUUID().toString();
        }
    }
}
