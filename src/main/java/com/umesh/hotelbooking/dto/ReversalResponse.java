package com.umesh.hotelbooking.dto;

import com.umesh.hotelbooking.entity.Reversal;
import com.umesh.hotelbooking.entity.ReversalReason;
import com.umesh.hotelbooking.entity.ReversalState;

import java.math.BigDecimal;

public record ReversalResponse(
        String reversalUid,
        String bookingUid,
        ReversalReason reason,
        BigDecimal amount,
        String currency,
        ReversalState state) {

    public static ReversalResponse from(Reversal reversal, String bookingUid) {
        return new ReversalResponse(reversal.getReversalUid(), bookingUid, reversal.getReason(),
                reversal.getAmount(), reversal.getCurrency(), reversal.getState());
    }
}
