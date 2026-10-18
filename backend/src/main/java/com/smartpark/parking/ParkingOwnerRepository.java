package com.smartpark.parking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParkingOwnerRepository extends JpaRepository<ParkingOwner, UUID> {

    Optional<ParkingOwner> findByUserId(UUID userId);

    Page<ParkingOwner> findByVerificationStatus(VerificationStatus status, Pageable pageable);
}
