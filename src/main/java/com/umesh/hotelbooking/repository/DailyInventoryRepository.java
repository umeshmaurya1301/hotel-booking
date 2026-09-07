package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.DailyInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyInventoryRepository extends JpaRepository<DailyInventory, Long> {

    Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);
}
