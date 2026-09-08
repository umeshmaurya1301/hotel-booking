package com.umesh.hotelbooking.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link PaymentReconciliationService} on a fixed schedule. Separate from the service
 * itself so the pass can also be triggered on demand by the admin endpoint and invoked
 * directly without racing a background timer.
 */
@Component
@ConditionalOnProperty(name = "payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationService reconciliationService;

    public PaymentReconciliationScheduler(PaymentReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.fixed-delay:15s}",
            initialDelayString = "${payment.reconciliation.fixed-delay:15s}")
    public void runScheduledReconciliation() {
        reconciliationService.run();
    }
}
