package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Property;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Property}. */
public interface PropertyStore {

    Property save(Property property);

    Optional<Property> findById(Long id);

    Optional<Property> findByPropertyUid(String propertyUid);

    /** Search matches on the normalised column, never on the display casing (see Property). */
    List<Property> findByCityNormalised(String cityNormalised);

    List<Property> findByPropertyGroupId(Long propertyGroupId);

    /**
     * The candidate fetch for {@code PropertySearchService} (design doc 10, task spec §5.1):
     * every property in a city, each with its room types <em>and</em> its amenities already
     * populated, in a bounded number of round trips that does not grow with the candidate count.
     *
     * <p>Stating it as one port method is deliberate. Loading two to-many collections eagerly
     * without cross-multiplying them is a real problem with a real, provider-specific answer —
     * and it is exactly the kind of answer that should not be visible to a caller. The search
     * service asks for search candidates; how a given store avoids N+1 without producing a
     * cartesian product is that store's business. {@code JpaPropertyStore} documents what it
     * costs there, and why the obvious single query is wrong.
     */
    List<Property> findForSearchByCityNormalised(String cityNormalised);

    List<Property> findAll();

    long count();
}
