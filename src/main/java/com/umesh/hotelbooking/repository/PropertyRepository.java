package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    Optional<Property> findByPropertyUid(String propertyUid);

    /** Search matches on the normalised column, never on the display casing (see Property). */
    List<Property> findByCityNormalised(String cityNormalised);

    List<Property> findByPropertyGroupId(Long propertyGroupId);

    /**
     * The candidate fetch for {@code PropertySearchService} (design doc 10, task spec §5.1),
     * two queries against the same persistence context rather than one.
     *
     * <p>The obvious one-query version — fetch-joining both {@code roomTypes} and {@code
     * amenities} in a single {@code select distinct p ... left join fetch p.roomTypes left
     * join fetch p.amenities} — looks correct and is the standard textbook answer, but
     * produces wrong data here: fetch-joining two collections on one root is a SQL cartesian
     * product (one row per room-type × amenity pair), and in this Hibernate version {@code
     * distinct} on that query did not collapse the duplication back out of the {@code
     * roomTypes} list — a property with 2 room types and 2 amenities came back with each room
     * type listed twice. That was caught by {@code PropertySearchServiceTest} asserting on
     * the actual room-type list, not merely on query count, which is exactly why that
     * assertion is there rather than trusting the fetch shape by inspection.
     *
     * <p>The fix is the well-established one for eagerly loading two to-many collections
     * without cross-multiplication: one fetch-join per collection, both against the same
     * managed entities. {@link #findWithRoomTypesByCityNormalised} loads the candidates with
     * {@code roomTypes} populated (a single-collection fetch join, safe on its own); {@link
     * #initialiseAmenities} then fetch-joins {@code amenities} for the same, already-managed
     * {@link Property} instances — same persistence context, so Hibernate merges the result
     * onto the objects already holding {@code roomTypes} rather than creating new ones. Two
     * queries, still no N+1 (each is one query regardless of candidate count), and no
     * dependence on a JPA provider's exact in-memory-distinct behaviour for a multi-collection
     * fetch join.
     *
     * <p>{@code amenities} being a {@code Set} and {@code roomTypes} a {@code List} is still
     * worth knowing even with the collections split apart: had they stayed in one query,
     * Hibernate throws {@code MultipleBagFetchException} for two {@code List}-typed
     * (bag-semantics) fetch-joined collections regardless of {@code distinct}.
     */
    default List<Property> findForSearchByCityNormalised(String cityNormalised) {
        List<Property> properties = findWithRoomTypesByCityNormalised(cityNormalised);
        return properties.isEmpty() ? properties : initialiseAmenities(properties);
    }

    /** The {@code where} clause matches {@code idx_property_city_rating}, the same index
     * {@link #findByCityNormalised} uses. Part one of {@link #findForSearchByCityNormalised} —
     * see that method's Javadoc; not meant to be called on its own from outside this repository. */
    @Query("select distinct p from Property p left join fetch p.roomTypes where p.cityNormalised = :cityNormalised")
    List<Property> findWithRoomTypesByCityNormalised(@Param("cityNormalised") String cityNormalised);

    /** Part two of {@link #findForSearchByCityNormalised} — see that method's Javadoc; not
     * meant to be called on its own from outside this repository. */
    @Query("select distinct p from Property p left join fetch p.amenities where p in :properties")
    List<Property> initialiseAmenities(@Param("properties") List<Property> properties);
}
