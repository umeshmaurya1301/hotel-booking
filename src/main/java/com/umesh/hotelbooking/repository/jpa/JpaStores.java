package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.repository.BookingStore;
import com.umesh.hotelbooking.repository.DailyInventoryStore;
import com.umesh.hotelbooking.repository.GuestStore;
import com.umesh.hotelbooking.repository.IdempotencyRecordStore;
import com.umesh.hotelbooking.repository.LedgerEntryStore;
import com.umesh.hotelbooking.repository.OwnerStore;
import com.umesh.hotelbooking.repository.PaymentStatusCheckStore;
import com.umesh.hotelbooking.repository.PaymentStore;
import com.umesh.hotelbooking.repository.PropertyGroupStore;
import com.umesh.hotelbooking.repository.PropertyStore;
import com.umesh.hotelbooking.repository.RefundStore;
import com.umesh.hotelbooking.repository.ReversalStore;
import com.umesh.hotelbooking.repository.RoomTypeStore;
import com.umesh.hotelbooking.repository.WebhookEventLogStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds every persistence port to its JPA adapter — the one place in the application where the
 * choice of persistence technology is actually made.
 *
 * <p><b>Why a configuration class rather than {@code @Component} on each adapter.</b> Two
 * reasons, and the second is the one that matters.
 *
 * <p>The practical reason: {@code @DataJpaTest} deliberately does not component-scan, so
 * {@code @Component} adapters would be invisible to exactly the tests that exercise the stores
 * against a real database. Those tests {@code @Import} this class and get the whole set.
 *
 * <p>The design reason: a swap should be a <em>readable</em> decision, not an emergent property
 * of which classes happen to carry a stereotype annotation. Everything the domain needs from
 * persistence is 14 lines here. Pointing a store at a different implementation is editing the
 * line that names it; running half the system on one technology and half on another — a
 * realistic migration, not a hypothetical — is editing the lines you are moving. That is the
 * "could be swapped later" the brief asks for, made concrete enough to actually do.
 *
 * <p>The adapters and their Spring Data interfaces are package-private on purpose: this class is
 * the only thing that constructs them, and the ports are the only types anything else can see.
 */
@Configuration
public class JpaStores {

    @Bean
    BookingStore bookingStore(JpaBookingRepository repository) {
        return new JpaBookingStore(repository);
    }

    @Bean
    DailyInventoryStore dailyInventoryStore(JpaDailyInventoryRepository repository) {
        return new JpaDailyInventoryStore(repository);
    }

    @Bean
    GuestStore guestStore(JpaGuestRepository repository) {
        return new JpaGuestStore(repository);
    }

    @Bean
    IdempotencyRecordStore idempotencyRecordStore(JpaIdempotencyRecordRepository repository) {
        return new JpaIdempotencyRecordStore(repository);
    }

    @Bean
    LedgerEntryStore ledgerEntryStore(JpaLedgerEntryRepository repository) {
        return new JpaLedgerEntryStore(repository);
    }

    @Bean
    OwnerStore ownerStore(JpaOwnerRepository repository) {
        return new JpaOwnerStore(repository);
    }

    @Bean
    PaymentStore paymentStore(JpaPaymentRepository repository) {
        return new JpaPaymentStore(repository);
    }

    @Bean
    PaymentStatusCheckStore paymentStatusCheckStore(JpaPaymentStatusCheckRepository repository) {
        return new JpaPaymentStatusCheckStore(repository);
    }

    @Bean
    PropertyGroupStore propertyGroupStore(JpaPropertyGroupRepository repository) {
        return new JpaPropertyGroupStore(repository);
    }

    @Bean
    PropertyStore propertyStore(JpaPropertyRepository repository) {
        return new JpaPropertyStore(repository);
    }

    @Bean
    RefundStore refundStore(JpaRefundRepository repository) {
        return new JpaRefundStore(repository);
    }

    @Bean
    ReversalStore reversalStore(JpaReversalRepository repository) {
        return new JpaReversalStore(repository);
    }

    @Bean
    RoomTypeStore roomTypeStore(JpaRoomTypeRepository repository) {
        return new JpaRoomTypeStore(repository);
    }

    @Bean
    WebhookEventLogStore webhookEventLogStore(JpaWebhookEventLogRepository repository) {
        return new JpaWebhookEventLogStore(repository);
    }
}
