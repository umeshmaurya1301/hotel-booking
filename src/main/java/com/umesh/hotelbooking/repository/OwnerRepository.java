package com.umesh.hotelbooking.repository;

import com.umesh.hotelbooking.entity.Owner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByOwnerUid(String ownerUid);
}
