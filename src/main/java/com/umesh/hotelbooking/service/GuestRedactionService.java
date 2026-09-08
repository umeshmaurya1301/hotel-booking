package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.dto.GuestResponse;
import com.umesh.hotelbooking.dto.RedactGuestResponse;
import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.exception.GuestNotFoundException;
import com.umesh.hotelbooking.repository.GuestRepository;
import com.umesh.hotelbooking.security.Masker;
import com.umesh.hotelbooking.web.RequestMeta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Erasure (design doc 12.6.3, POST /api/v1/admin/guests/{id}/redact): tombstones a guest's
 * identifying fields in place. Touches nothing else — no booking, no payment, no ledger entry,
 * no reversal — which is exactly what makes the financial trail survive an erasure intact
 * (see {@code GuestRedactionIntegrityTest}).
 *
 * <p>Idempotent by design, not merely by the idempotency layer wrapping it: replaying erasure
 * on an already-redacted guest returns the existing {@code redactedAt} unchanged rather than
 * erroring. An erasure request replayed by a support tool must not fail. It is also wrapped in
 * {@link IdempotencyService}, unlike the sweeper and reconciliation triggers Phase 6
 * deliberately left un-deduped (see that phase's 16.5 findings) — erasure is a once-per-subject
 * operation whose replay should return the stored response, which is exactly the shape
 * idempotency layer (a) was built for.
 */
@Service
public class GuestRedactionService {

    private static final String TOMBSTONE = Masker.TOMBSTONE;

    private final GuestRepository guestRepository;
    private final IdempotencyService idempotencyService;
    private final Clock clock;

    public GuestRedactionService(GuestRepository guestRepository, IdempotencyService idempotencyService, Clock clock) {
        this.guestRepository = guestRepository;
        this.idempotencyService = idempotencyService;
        this.clock = clock;
    }

    @Transactional
    public RedactGuestResponse redact(String guestUid, RequestMeta meta, Object requestPayload) {
        var cached = idempotencyService.begin(meta, requestPayload, RedactGuestResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        Guest guest = guestRepository.findByGuestUid(guestUid)
                .orElseThrow(() -> new GuestNotFoundException(guestUid));

        if (guest.getRedactedAt() == null) {
            // A tombstone reading "[REDACTED]" distinguishes "erased on request" from "never
            // supplied" — nulling the strings instead would make an auditable erasure
            // indistinguishable from a guest who simply never gave this information.
            guest.setFullName(TOMBSTONE);
            guest.setEmail(TOMBSTONE);
            guest.setPhone(TOMBSTONE);
            guest.setAddress(TOMBSTONE);
            guest.setDateOfBirth(null);
            guest.setRedactedAt(Instant.now(clock));
            guestRepository.save(guest);
        }

        RedactGuestResponse response = RedactGuestResponse.from(guest);
        idempotencyService.complete(meta.msgId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public GuestResponse find(String guestUid) {
        Guest guest = guestRepository.findByGuestUid(guestUid)
                .orElseThrow(() -> new GuestNotFoundException(guestUid));
        return GuestResponse.from(guest);
    }
}
