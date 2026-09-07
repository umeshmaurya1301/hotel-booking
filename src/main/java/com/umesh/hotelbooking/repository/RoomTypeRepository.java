package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {

    Optional<RoomType> findByRoomTypeUid(String roomTypeUid);

    List<RoomType> findByPropertyId(Long propertyId);
}
