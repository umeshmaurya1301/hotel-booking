package com.umesh.hotelbooking.dto;

import java.time.LocalDate;

public record RepriceResponse(
        String roomTypeUid,
        LocalDate from,
        LocalDate to,
        String strategyCode,
        int nightsRepriced) {
}
