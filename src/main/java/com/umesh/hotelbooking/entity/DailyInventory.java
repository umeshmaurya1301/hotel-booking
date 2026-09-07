package com.umesh.hotelbooking.entity;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One room-night: how many units of a room type exist on a given stay date, how many are
 * already booked, and what that specific night costs.
 *
 * <p>Three deliberate choices, each carrying weight elsewhere in the design:
 *
 * <p><b>The check constraints are the correctness backstop</b> (design doc 5.2.4, layer 3).
 * {@code booked_units <= total_units} is enforced by the database, so overbooking is
 * impossible even from a hand-written query or a future code path that forgets the rules.
 * Application logic can be wrong; a check constraint cannot be bypassed.
 *
 * <p><b>No {@code @Version} column</b> (design doc 4.1/5.2). Reservation is a single atomic
 * conditional UPDATE — {@code SET booked_units = booked_units + :n WHERE ... AND booked_units
 * + :n <= total_units} — which has no read-then-write window and therefore needs no
 * optimistic lock. A version column nothing reads would be dead weight.
 *
 * <p><b>{@code roomTypeId} is a plain column, not a {@code @ManyToOne}</b>. The reservation
 * path in the next phase updates these rows by {@code (room_type_id, stay_date)} in bulk and
 * never needs the {@link RoomType} object; keeping it an id avoids dragging a lazy proxy
 * onto the hottest path in the system.
 */
@Entity
@Table(name = "daily_inventory",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_slot",
                columnNames = {"room_type_id", "stay_date"}),
        indexes = @Index(name = "idx_inventory_lookup", columnList = "room_type_id, stay_date"),
        check = {
                @CheckConstraint(name = "ck_not_overbooked", constraint = "booked_units <= total_units"),
                @CheckConstraint(name = "ck_non_negative", constraint = "booked_units >= 0"),
                @CheckConstraint(name = "ck_price_positive", constraint = "price_per_unit > 0")
        })
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

    @Column(name = "total_units", nullable = false)
    private int totalUnits;

    @Builder.Default
    @Column(name = "booked_units", nullable = false)
    private int bookedUnits = 0;

    @Column(name = "price_per_unit", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(nullable = false, length = 3)
    private String currency;

    public int availableUnits() {
        return totalUnits - bookedUnits;
    }

    public boolean hasCapacityFor(int units) {
        return bookedUnits + units <= totalUnits;
    }
}
