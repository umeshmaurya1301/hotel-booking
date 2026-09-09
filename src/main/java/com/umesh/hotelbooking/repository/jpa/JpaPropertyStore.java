package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.repository.PropertyStore;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link PropertyStore}. */
class JpaPropertyStore implements PropertyStore {

    private final JpaPropertyRepository repository;

    JpaPropertyStore(JpaPropertyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Property save(Property property) {
        return repository.save(property);
    }

    @Override
    public Optional<Property> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Property> findByPropertyUid(String propertyUid) {
        return repository.findByPropertyUid(propertyUid);
    }

    @Override
    public List<Property> findByCityNormalised(String cityNormalised) {
        return repository.findByCityNormalised(cityNormalised);
    }

    @Override
    public List<Property> findByPropertyGroupId(Long propertyGroupId) {
        return repository.findByPropertyGroupId(propertyGroupId);
    }

    /**
     * Two queries against the same persistence context, not one — the one place in this package
     * where an adapter does more than forward, and the reason the port declares a single method
     * instead of exposing the pieces.
     *
     * <p>The obvious one-query version — fetch-joining both {@code roomTypes} and
     * {@code amenities} in a single {@code select distinct p ... left join fetch p.roomTypes
     * left join fetch p.amenities} — looks correct and is the standard textbook answer, but
     * produces wrong data here: fetch-joining two collections on one root is a SQL cartesian
     * product (one row per room-type × amenity pair), and in this Hibernate version
     * {@code distinct} on that query did not collapse the duplication back out of the
     * {@code roomTypes} list — a property with 2 room types and 2 amenities came back with each
     * room type listed twice. That was caught by {@code PropertySearchServiceTest} asserting on
     * the actual room-type list, not merely on query count, which is exactly why that assertion
     * is there rather than trusting the fetch shape by inspection.
     *
     * <p>The fix is the well-established one for eagerly loading two to-many collections without
     * cross-multiplication: one fetch-join per collection, both against the same managed
     * entities. {@code findWithRoomTypesByCityNormalised} loads the candidates with
     * {@code roomTypes} populated (a single-collection fetch join, safe on its own);
     * {@code initialiseAmenities} then fetch-joins {@code amenities} for the same,
     * already-managed {@link Property} instances — same persistence context, so Hibernate merges
     * the result onto the objects already holding {@code roomTypes} rather than creating new
     * ones. Two queries, still no N+1 (each is one query regardless of candidate count), and no
     * dependence on a JPA provider's exact in-memory-distinct behaviour for a multi-collection
     * fetch join.
     *
     * <p>{@code amenities} being a {@code Set} and {@code roomTypes} a {@code List} is still
     * worth knowing even with the collections split apart: had they stayed in one query,
     * Hibernate throws {@code MultipleBagFetchException} for two {@code List}-typed
     * (bag-semantics) fetch-joined collections regardless of {@code distinct}.
     *
     * <p>All of which is Hibernate's problem, not the search service's — which is the argument
     * for the port boundary in one paragraph.
     */
    @Override
    public List<Property> findForSearchByCityNormalised(String cityNormalised) {
        List<Property> properties = repository.findWithRoomTypesByCityNormalised(cityNormalised);
        return properties.isEmpty() ? properties : repository.initialiseAmenities(properties);
    }

    @Override
    public List<Property> findAll() {
        return repository.findAll();
    }

    @Override
    public long count() {
        return repository.count();
    }
}
