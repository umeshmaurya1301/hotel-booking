package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.DailyInventory;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InventoryResponse(
        LocalDate stayDate,
        int totalUnits,
        int bookedUnits,
        int availableUnits,
        BigDecimal pricePerUnit,
        String currency) {

    public static InventoryResponse from(DailyInventory inventory) {
        return new InventoryResponse(
                inventory.getStayDate(),
                inventory.getTotalUnits(),
                inventory.getBookedUnits(),
                inventory.availableUnits(),
                inventory.getPricePerUnit(),
                inventory.getCurrency());
    }
}
