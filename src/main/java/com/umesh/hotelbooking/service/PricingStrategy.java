package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.entity.RoomType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Computes what one unit of a room type costs on one specific night (design doc 4.2.1).
 *
 * <p>Strategies are evaluated at <em>materialisation</em> time and their answer is written
 * onto the {@code daily_inventory} row, not evaluated when a guest books. Three consequences
 * follow from that, and they are the reason the strategy attaches here rather than on the
 * booking path:
 *
 * <ul>
 *   <li>Booking reads a stored number — no strategy evaluation on the hot path.</li>
 *   <li>An admin can override a single night's rate without touching strategy code.</li>
 *   <li>Changing a strategy affects future materialisation only. Existing bookings hold
 *       their line-item snapshot and existing rows keep their price until repriced
 *       explicitly.</li>
 * </ul>
 *
 * <p>Adding a strategy is one new {@code @Component} implementing this interface: the
 * registry discovers it by injection, and no existing class changes.
 */
public interface PricingStrategy {

    BigDecimal priceFor(RoomType roomType, LocalDate stayDate);

    /** Stable identifier used in configuration and in the reprice endpoint. */
    String strategyCode();
}
