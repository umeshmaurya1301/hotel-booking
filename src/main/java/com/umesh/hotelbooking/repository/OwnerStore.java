package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Owner;

import java.util.Optional;

/** Persistence port for {@link Owner}, the root of the ownership hierarchy. */
public interface OwnerStore {

    Owner save(Owner owner);

    Optional<Owner> findByOwnerUid(String ownerUid);
}
