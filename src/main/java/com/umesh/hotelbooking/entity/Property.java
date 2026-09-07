package com.umesh.hotelbooking.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One hotel: a building in a city, belonging to exactly one {@link PropertyGroup}.
 *
 * <p>{@code zoneId} is load-bearing rather than descriptive. A stay date is a calendar
 * concept — "the night of the 14th" at this hotel — so every "what is today" question about
 * this property must be answered in this zone, not the server's (design doc 4.5). Inventory
 * materialisation starts from {@code LocalDate.now(clock.withZone(property.zone()))} for
 * exactly this reason.
 *
 * <p>{@code cityNormalised} is stored rather than computed at query time so that search can
 * match "Bengaluru", "bengaluru" and " Bengaluru " against an indexed column; {@code city}
 * keeps the original casing for display.
 */
@Entity
@Table(name = "properties", indexes = {
        @Index(name = "idx_property_city_rating", columnList = "city_normalised, star_rating")
})
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_group_id", nullable = false)
    private PropertyGroup propertyGroup;

    @NotBlank
    @Column(nullable = false, length = 200)
    private String name;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String city;

    @Column(name = "city_normalised", nullable = false, length = 120)
    private String cityNormalised;

    @Column(length = 160)
    private String locality;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Min(1)
    @Max(5)
    @Column(name = "star_rating", nullable = false)
    private int starRating;

    /** IANA zone id, e.g. {@code Asia/Kolkata}. See the class javadoc — this is not cosmetic. */
    @NotBlank
    @Column(name = "zone_id", nullable = false, length = 60)
    private String zoneId;

    @Column(nullable = false, length = 3)
    private String currency;

    @ElementCollection(targetClass = Amenity.class, fetch = FetchType.LAZY)
    @CollectionTable(name = "property_amenities", joinColumns = @JoinColumn(name = "property_id"))
    @Column(name = "amenity", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Amenity> amenities = new LinkedHashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "property", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoomType> roomTypes = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void onWrite() {
        if (propertyUid == null) {
            propertyUid = UUID.randomUUID().toString();
        }
        if (currency == null) {
            currency = "INR";
        }
        cityNormalised = normalise(city);
    }

    /**
     * Trimmed, lowercased, internal-whitespace-collapsed, for search matching. Kept as a
     * static helper so a search query can normalise its input the same way this normalises
     * what it stores — the two must agree or the index is useless.
     */
    public static String normalise(String value) {
        return value == null ? null : value.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    /** Keeps both sides of the bidirectional association in sync. */
    public void addRoomType(RoomType roomType) {
        roomTypes.add(roomType);
        roomType.setProperty(this);
    }
}
