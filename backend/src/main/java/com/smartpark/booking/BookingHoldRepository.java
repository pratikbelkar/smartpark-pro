package com.smartpark.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingHoldRepository extends JpaRepository<BookingHold, UUID> {

    @Query("""
            select h from BookingHold h
            where h.slot.id in :slotIds
              and h.status = com.smartpark.booking.HoldStatus.ACTIVE
              and h.startTime < :end and h.endTime > :start
            """)
    List<BookingHold> findActiveOverlaps(List<UUID> slotIds, LocalDateTime start, LocalDateTime end);

    List<BookingHold> findByStatusAndExpiresAtBefore(HoldStatus status, LocalDateTime now);
}
