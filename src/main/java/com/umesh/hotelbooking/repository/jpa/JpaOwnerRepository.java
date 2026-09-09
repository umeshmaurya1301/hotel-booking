package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data JPA queries behind {@link JpaOwnerStore}. Not injected outside this package. */
interface JpaOwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByOwnerUid(String ownerUid);
}
