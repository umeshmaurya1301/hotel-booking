package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaRoomTypeStore}. Not injected outside this package. */
interface JpaRoomTypeRepository extends JpaRepository<RoomType, Long> {

    Optional<RoomType> findByRoomTypeUid(String roomTypeUid);

    List<RoomType> findByPropertyId(Long propertyId);
}
