package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Placeholder for the per-night inventory row — enough to give {@code
 * DailyInventoryRepository} a concrete target. Total/booked units and per-night pricing are
 * built in the inventory-materialisation phase.
 *
 * <p>Unlike the other placeholders, this deliberately has no separate UUID business id: its
 * natural, externally-meaningful key is the (roomTypeId, stayDate) pair, enforced here as a
 * unique constraint rather than invented as a synthetic identifier nothing would ever look
 * up by.
 */
@Entity
@Table(name = "daily_inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_type_id", "stay_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;
}
