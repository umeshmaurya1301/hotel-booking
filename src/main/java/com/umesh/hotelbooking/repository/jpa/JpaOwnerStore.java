package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Owner;
import com.umesh.hotelbooking.repository.OwnerStore;

import java.util.Optional;

/** JPA adapter for {@link OwnerStore}. */
class JpaOwnerStore implements OwnerStore {

    private final JpaOwnerRepository repository;

    JpaOwnerStore(JpaOwnerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Owner save(Owner owner) {
        return repository.save(owner);
    }

    @Override
    public Optional<Owner> findByOwnerUid(String ownerUid) {
        return repository.findByOwnerUid(ownerUid);
    }
}
