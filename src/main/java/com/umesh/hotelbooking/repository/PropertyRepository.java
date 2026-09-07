package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByPropertyUid(String propertyUid);

    /** Search matches on the normalised column, never on the display casing (see Property). */
    List<Property> findByCityNormalised(String cityNormalised);

    List<Property> findByPropertyGroupId(Long propertyGroupId);
}
