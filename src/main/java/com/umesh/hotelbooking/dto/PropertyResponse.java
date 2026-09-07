package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.entity.Property;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A property as returned to an admin caller. Exposes business uids only — the Long primary
 * keys never cross this boundary.
 */
public record PropertyResponse(
        String propertyUid,
        String propertyGroupUid,
        String ownerUid,
        String name,
        String city,
        String locality,
        BigDecimal latitude,
        BigDecimal longitude,
        int starRating,
        String zoneId,
        String currency,
        Set<Amenity> amenities,
        List<RoomTypeResponse> roomTypes) {

    /**
     * Must be called inside the transaction that loaded {@code property}.
     *
     * <p>The lazy collections are <em>copied</em>, not referenced. Holding a reference to a
     * Hibernate {@code PersistentSet} would defer its initialisation until Jackson serialises
     * the response — by which time the session is closed and serialisation fails. Copying
     * forces the load to happen here, while there is still a session to load it with.
     */
    public static PropertyResponse from(Property property) {
        return new PropertyResponse(
                property.getPropertyUid(),
                property.getPropertyGroup().getPropertyGroupUid(),
                property.getPropertyGroup().getOwner().getOwnerUid(),
                property.getName(),
                property.getCity(),
                property.getLocality(),
                property.getLatitude(),
                property.getLongitude(),
                property.getStarRating(),
                property.getZoneId(),
                property.getCurrency(),
                new LinkedHashSet<>(property.getAmenities()),
                property.getRoomTypes().stream().map(RoomTypeResponse::from).toList());
    }
}
