package com.umesh.hotelbooking.security;

import com.umesh.hotelbooking.config.EncryptionProperties;
import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.repository.GuestStore;
import com.umesh.hotelbooking.repository.jpa.JpaStores;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The assertion that actually proves encryption at rest: read the raw column with a native
 * query and check the plaintext is not in it.
 *
 * <p>{@code FieldCipherTest} proves the cipher works in isolation, which is a different and
 * much weaker claim — every one of its tests would still pass if {@code Guest} had never been
 * annotated with {@code @Convert}, or if Hibernate had quietly instantiated the converter
 * itself without the {@code FieldCipher} dependency injected. Only going through a real
 * persistence context and then looking at what landed in the database distinguishes "the
 * cipher is correct" from "the data is encrypted", and the second is the claim design doc 12.6
 * is actually making.
 */
@DataJpaTest
@Import({FieldCipher.class, EncryptedStringConverter.class, EncryptedLocalDateConverter.class, JpaStores.class})
@EnableConfigurationProperties(EncryptionProperties.class)
class GuestEncryptionAtRestTest {

    @Autowired
    private GuestStore guestStore;
    @Autowired
    private EntityManager entityManager;

    private String rawColumn(String column, String guestUid) {
        Object value = entityManager
                .createNativeQuery("select " + column + " from guests where guest_uid = :uid")
                .setParameter("uid", guestUid)
                .getSingleResult();
        return value == null ? null : value.toString();
    }

    private Guest persistGuest() {
        Guest guest = guestStore.saveAndFlush(Guest.builder()
                .guestUid("guest-encryption-1")
                .fullName("Asha Menon")
                .email("asha@example.com")
                .phone("+919876543210")
                .address("42 Residency Road, Bengaluru 560025")
                .dateOfBirth(LocalDate.of(1991, 4, 17))
                .build());
        // Force a real database round-trip on the next read rather than the first-level cache
        // handing back the instance still holding plaintext in memory.
        entityManager.flush();
        entityManager.clear();
        return guest;
    }

    @Test
    void noPiiColumnHoldsItsPlaintextValueInTheDatabase() {
        persistGuest();

        assertThat(rawColumn("full_name", "guest-encryption-1"))
                .as("a stolen database file must not hand over the guest's name")
                .doesNotContain("Asha").startsWith("v1:");
        assertThat(rawColumn("email", "guest-encryption-1"))
                .doesNotContain("asha@example.com").startsWith("v1:");
        assertThat(rawColumn("phone", "guest-encryption-1"))
                .doesNotContain("9876543210").startsWith("v1:");
        assertThat(rawColumn("address", "guest-encryption-1"))
                .doesNotContain("Residency Road").doesNotContain("560025").startsWith("v1:");
        assertThat(rawColumn("date_of_birth", "guest-encryption-1"))
                .doesNotContain("1991").startsWith("v1:");
    }

    @Test
    void theSameRowReadsBackThroughJpaAsPlaintext() {
        persistGuest();

        Guest reloaded = guestStore.findByGuestUid("guest-encryption-1").orElseThrow();

        assertThat(reloaded.getFullName()).isEqualTo("Asha Menon");
        assertThat(reloaded.getEmail()).isEqualTo("asha@example.com");
        assertThat(reloaded.getPhone()).isEqualTo("+919876543210");
        assertThat(reloaded.getAddress()).isEqualTo("42 Residency Road, Bengaluru 560025");
        assertThat(reloaded.getDateOfBirth()).isEqualTo(LocalDate.of(1991, 4, 17));
    }

    /**
     * {@code guestUid} is deliberately not encrypted — it is the opaque handle every lookup and
     * every append-only table uses, and encrypting it would break {@code findByGuestUid}
     * outright. That it stays queryable is the point, not an oversight.
     */
    @Test
    void theOpaqueBusinessIdIsNotEncryptedSoLookupsStillWork() {
        persistGuest();

        assertThat(rawColumn("guest_uid", "guest-encryption-1")).isEqualTo("guest-encryption-1");
        assertThat(guestStore.findByGuestUid("guest-encryption-1")).isPresent();
    }

    @Test
    void aGuestWhoSuppliedNothingStoresNullsRatherThanCiphertext() {
        guestStore.saveAndFlush(Guest.builder().guestUid("guest-encryption-empty").build());
        entityManager.flush();
        entityManager.clear();

        assertThat(rawColumn("full_name", "guest-encryption-empty")).isNull();
        assertThat(rawColumn("date_of_birth", "guest-encryption-empty")).isNull();
        assertThat(guestStore.findByGuestUid("guest-encryption-empty").orElseThrow().getFullName())
                .isNull();
    }

    /**
     * Erasure and encryption are separate concerns and must both still hold: the tombstone is
     * itself encrypted (so the column never reveals it), while {@code redactedAt} stays a
     * plain timestamp, which is what keeps "this guest was erased" auditable without decrypting
     * anything.
     */
    @Test
    void anErasedGuestsTombstoneIsEncryptedButTheErasureItselfStaysVisible() {
        Guest guest = persistGuest();
        Guest loaded = guestStore.findByGuestUid(guest.getGuestUid()).orElseThrow();
        loaded.setFullName(Masker.TOMBSTONE);
        loaded.setEmail(Masker.TOMBSTONE);
        loaded.setRedactedAt(java.time.Instant.parse("2026-09-09T00:00:00Z"));
        guestStore.saveAndFlush(loaded);
        entityManager.flush();
        entityManager.clear();

        assertThat(rawColumn("full_name", "guest-encryption-1"))
                .doesNotContain(Masker.TOMBSTONE).startsWith("v1:");
        assertThat(rawColumn("redacted_at", "guest-encryption-1"))
                .as("the erasure marker is not PII and stays readable for audit")
                .isNotNull().doesNotStartWith("v1:");
        assertThat(guestStore.findByGuestUid("guest-encryption-1").orElseThrow().getFullName())
                .isEqualTo(Masker.TOMBSTONE);
    }
}
