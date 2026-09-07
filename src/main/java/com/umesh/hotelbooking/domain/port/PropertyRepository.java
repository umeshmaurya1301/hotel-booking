package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.property.Property;
import com.umesh.hotelbooking.domain.vo.PropertyId;

import java.util.Optional;

/**
 * Persistence port for {@link Property}. Minimal for Phase 1; the onboarding phase extends
 * this with lookups by owner/group and by search criteria.
 */
public interface PropertyRepository {

    Property save(Property property);

    Optional<Property> findById(PropertyId id);
}
