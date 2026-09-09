package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaPropertyStore}. Not injected outside this package. */
interface JpaPropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByPropertyUid(String propertyUid);

    /** Search matches on the normalised column, never on the display casing (see Property). */
    List<Property> findByCityNormalised(String cityNormalised);

    List<Property> findByPropertyGroupId(Long propertyGroupId);

    /** The {@code where} clause matches {@code idx_property_city_rating}, the same index
     * {@link #findByCityNormalised} uses. Half of the two-query candidate fetch — see
     * {@link JpaPropertyStore#findForSearchByCityNormalised}. */
    @Query("select distinct p from Property p left join fetch p.roomTypes where p.cityNormalised = :cityNormalised")
    List<Property> findWithRoomTypesByCityNormalised(@Param("cityNormalised") String cityNormalised);

    /** The other half — see {@link JpaPropertyStore#findForSearchByCityNormalised}. */
    @Query("select distinct p from Property p left join fetch p.amenities where p in :properties")
    List<Property> initialiseAmenities(@Param("properties") List<Property> properties);
}
