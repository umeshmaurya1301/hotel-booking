package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.PropertyGroup;

import java.util.Optional;

/** Persistence port for {@link PropertyGroup} — the group of one that an independent hotel
 * gets, and the group of many that a chain gets, stored identically. */
public interface PropertyGroupStore {

    PropertyGroup save(PropertyGroup propertyGroup);

    Optional<PropertyGroup> findByPropertyGroupUid(String propertyGroupUid);
}
