package com.umesh.hotelbooking.entity;

/**
 * Facilities a property offers. Modelled as an enum held in an {@code @ElementCollection}
 * rather than as its own entity with a join table: amenities are a closed, slow-changing
 * vocabulary with no attributes of their own, so a separate aggregate would add a table and
 * a lifecycle without adding anything a search filter could use.
 */
public enum Amenity {
    WIFI,
    POOL,
    GYM,
    SPA,
    PARKING,
    RESTAURANT,
    BAR,
    ROOM_SERVICE,
    AIR_CONDITIONING,
    PET_FRIENDLY,
    AIRPORT_SHUTTLE,
    BREAKFAST_INCLUDED,
    BUSINESS_CENTRE
}
