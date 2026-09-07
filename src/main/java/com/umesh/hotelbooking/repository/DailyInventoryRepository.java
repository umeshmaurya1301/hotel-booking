package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.DailyInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyInventoryRepository extends JpaRepository<DailyInventory, Long> {

    Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);

    /** {@code from} and {@code to} are both inclusive, matching JPA's BETWEEN semantics. */
    List<DailyInventory> findByRoomTypeIdAndStayDateBetween(Long roomTypeId, LocalDate from, LocalDate to);

    /**
     * The last night already materialised for a room type — the starting point for rolling
     * the horizon forward without re-creating rows that already exist.
     */
    @Query("select max(d.stayDate) from DailyInventory d where d.roomTypeId = :roomTypeId")
    Optional<LocalDate> findLastMaterialisedDate(@Param("roomTypeId") Long roomTypeId);

    long countByRoomTypeId(Long roomTypeId);
}
