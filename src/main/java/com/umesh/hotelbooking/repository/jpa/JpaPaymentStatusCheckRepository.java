package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.PaymentStatusCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data JPA queries behind {@link JpaPaymentStatusCheckStore}. Not injected outside this
 * package. */
interface JpaPaymentStatusCheckRepository extends JpaRepository<PaymentStatusCheck, Long> {

    List<PaymentStatusCheck> findByPaymentIdOrderByAttemptNoAsc(Long paymentId);
}
