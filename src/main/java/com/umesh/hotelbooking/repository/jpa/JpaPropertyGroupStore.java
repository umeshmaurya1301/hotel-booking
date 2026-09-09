package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.PropertyGroup;
import com.umesh.hotelbooking.repository.PropertyGroupStore;

import java.util.Optional;

/** JPA adapter for {@link PropertyGroupStore}. */
class JpaPropertyGroupStore implements PropertyGroupStore {

    private final JpaPropertyGroupRepository repository;

    JpaPropertyGroupStore(JpaPropertyGroupRepository repository) {
        this.repository = repository;
    }

    @Override
    public PropertyGroup save(PropertyGroup propertyGroup) {
        return repository.save(propertyGroup);
    }

    @Override
    public Optional<PropertyGroup> findByPropertyGroupUid(String propertyGroupUid) {
        return repository.findByPropertyGroupUid(propertyGroupUid);
    }
}
