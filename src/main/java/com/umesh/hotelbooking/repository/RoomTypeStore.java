package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.RoomType;

import java.util.List;
import java.util.Optional;

/** Persistence port for {@link RoomType}. */
public interface RoomTypeStore {

    Optional<RoomType> findById(Long id);

    Optional<RoomType> findByRoomTypeUid(String roomTypeUid);

    List<RoomType> findByPropertyId(Long propertyId);
}
