package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.event.PropertyOnboardedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Records completed onboardings.
 *
 * <p>{@code AFTER_COMMIT} is the load-bearing part: an event handled inside the transaction
 * could act on an onboarding that then rolls back, announcing a property that does not
 * exist. The notification and audit consumers added in later phases attach at this same
 * phase for the same reason (design doc 14, 19).
 */
@Component
public class PropertyOnboardedListener {

    private static final Logger log = LoggerFactory.getLogger(PropertyOnboardedListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPropertyOnboarded(PropertyOnboardedEvent event) {
        log.info("Property onboarded: propertyUid={} group={} city={} roomTypes={} nightsMaterialised={}",
                event.propertyUid(), event.propertyGroupUid(), event.city(),
                event.roomTypeCount(), event.nightsMaterialised());
    }
}
