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
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sellable class of room within a property — "Deluxe King", 10 units, ₹8000/night.
 *
 * <p>{@code basePricePerNight} is the input to a {@code PricingStrategy}, not the price a
 * guest pays. The price actually charged for a given night lives on that night's
 * {@link DailyInventory} row, written at materialisation time (design doc 4.2.1), which is
 * what makes weekend, seasonal and per-night admin overrides expressible at all.
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String name;

    @Min(1)
    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Min(1)
    @Column(name = "max_guests", nullable = false)
    private int maxGuests;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Column(name = "base_price_per_night", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePricePerNight;

    @PrePersist
    private void onCreate() {
        if (roomTypeUid == null) {
            roomTypeUid = UUID.randomUUID().toString();
        }
    }
}
