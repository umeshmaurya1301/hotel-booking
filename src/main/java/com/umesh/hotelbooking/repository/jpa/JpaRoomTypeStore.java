package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.repository.RoomTypeStore;

import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link RoomTypeStore}. */
class JpaRoomTypeStore implements RoomTypeStore {

    private final JpaRoomTypeRepository repository;

    JpaRoomTypeStore(JpaRoomTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<RoomType> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<RoomType> findByRoomTypeUid(String roomTypeUid) {
        return repository.findByRoomTypeUid(roomTypeUid);
    }

    @Override
    public List<RoomType> findByPropertyId(Long propertyId) {
        return repository.findByPropertyId(propertyId);
    }
}
