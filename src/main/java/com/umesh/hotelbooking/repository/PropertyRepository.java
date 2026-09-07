package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByPropertyUid(String propertyUid);
}
