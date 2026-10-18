package com.smartpark.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingSessionRepository extends JpaRepository<ParkingSession, UUID> {

    Optional<ParkingSession> findByBookingId(UUID bookingId);

    List<ParkingSession> findBySlotIdAndSessionStatus(UUID slotId, String sessionStatus);

    @org.springframework.data.jpa.repository.Query("""
            select s from ParkingSession s
            where s.booking.location.owner.id = :ownerId
              and s.sessionStatus = 'ACTIVE'
            """)
    List<ParkingSession> findActiveByOwner(UUID ownerId);
}
