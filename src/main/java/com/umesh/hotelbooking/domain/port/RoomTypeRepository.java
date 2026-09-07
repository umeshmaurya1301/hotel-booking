package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.property.RoomType;
import com.umesh.hotelbooking.domain.vo.RoomTypeId;

import java.util.Optional;

/**
 * Persistence port for {@link RoomType}. Minimal for Phase 1; the onboarding phase extends
 * this with lookups by property.
 */
public interface RoomTypeRepository {

    RoomType save(RoomType roomType);

    Optional<RoomType> findById(RoomTypeId id);
}
