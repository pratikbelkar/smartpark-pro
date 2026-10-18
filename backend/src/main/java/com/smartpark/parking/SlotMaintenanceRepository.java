package com.smartpark.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface SlotMaintenanceRepository extends JpaRepository<SlotMaintenance, Long> {

    @Query("""
            select m from SlotMaintenance m
            where m.slot.id in :slotIds
              and m.startsAt < :end
              and (m.endsAt is null or m.endsAt > :start)
            """)
    List<SlotMaintenance> findOverlapping(List<UUID> slotIds, LocalDateTime start, LocalDateTime end);
}
