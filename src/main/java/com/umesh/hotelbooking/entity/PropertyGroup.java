package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A collection of properties under one owner — "Taj Group", or the group of exactly one
 * created for an independent hotel.
 *
 * <p>This type is the answer to the brief's structural requirement (design doc 3.1): a single
 * property is not a special case in the code, it is a group containing one property. Every
 * property has a group, so nothing downstream ever branches on
 * {@code if (isChain)} — the uniform hierarchy is what removes the branch, not a conditional
 * that handles both shapes.
 */
@Entity
@Table(name = "property_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "property_group_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String propertyGroupUid;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private Owner owner;

    /**
     * Which settlement provider this group's payments route through (design doc 6.4). Written
     * at onboarding, read by the payment gateway router in a later phase — this field is what
     * gives provider routing a real domain justification rather than making it decoration.
     */
    @Column(name = "settlement_bank_code", length = 40)
    private String settlementBankCode;

    @PrePersist
    private void onCreate() {
        if (propertyGroupUid == null) {
            propertyGroupUid = UUID.randomUUID().toString();
        }
    }
}
