package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.PropertyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyGroupRepository extends JpaRepository<PropertyGroup, Long> {

    Optional<PropertyGroup> findByPropertyGroupUid(String propertyGroupUid);
}
