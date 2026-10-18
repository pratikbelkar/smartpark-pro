package com.smartpark.qr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QrPassRepository extends JpaRepository<QrPass, UUID> {

    Optional<QrPass> findByBookingId(UUID bookingId);

    Optional<QrPass> findByTokenHash(String tokenHash);
}
