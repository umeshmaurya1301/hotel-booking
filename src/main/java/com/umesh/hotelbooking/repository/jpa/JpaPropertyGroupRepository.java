package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.PropertyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaPropertyGroupStore}. Not injected outside this
 * package. */
interface JpaPropertyGroupRepository extends JpaRepository<PropertyGroup, Long> {

    Optional<PropertyGroup> findByPropertyGroupUid(String propertyGroupUid);
}
