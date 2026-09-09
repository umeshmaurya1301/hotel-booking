package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.PaymentStatusCheck;

import java.util.List;

/** Persistence port for {@link PaymentStatusCheck}. Append-only, like every other audit trail
 * here — see {@link LedgerEntryStore}. */
public interface PaymentStatusCheckStore {

    PaymentStatusCheck save(PaymentStatusCheck statusCheck);

    List<PaymentStatusCheck> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
}
