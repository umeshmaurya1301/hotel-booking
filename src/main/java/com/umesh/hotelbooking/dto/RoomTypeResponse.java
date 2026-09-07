package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.RoomType;

import java.math.BigDecimal;

public record RoomTypeResponse(
        String roomTypeUid,
        String name,
        int totalUnits,
        int maxGuests,
        BigDecimal basePricePerNight) {

    public static RoomTypeResponse from(RoomType roomType) {
        return new RoomTypeResponse(
                roomType.getRoomTypeUid(),
                roomType.getName(),
                roomType.getTotalUnits(),
                roomType.getMaxGuests(),
                roomType.getBasePricePerNight());
    }
}
