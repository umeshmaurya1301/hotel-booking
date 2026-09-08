package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Amenity;

import java.util.List;
import java.util.Set;

/**
 * One property surviving the search filter chain, naming every room type that matched (design
 * doc 10.1, task spec §1) — never just a bare "this property matched".
 */
public record PropertySearchResult(
        String propertyUid,
        String name,
        String city,
        String locality,
        int starRating,
        Set<Amenity> amenities,
        List<RoomTypeSearchResult> roomTypes) {
}
