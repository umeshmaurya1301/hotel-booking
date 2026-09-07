package com.umesh.hotelbooking.domain.port;

import com.umesh.hotelbooking.domain.model.guest.Guest;
import com.umesh.hotelbooking.domain.vo.GuestId;

import java.util.Optional;

/**
 * Persistence port for {@link Guest}. Minimal for Phase 1; a later phase extends this with
 * the profile-redaction operation referenced in the design doc.
 */
public interface GuestRepository {

    Guest save(Guest guest);

    Optional<Guest> findById(GuestId id);
}
