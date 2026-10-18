package com.smartpark.parking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ParkingPricingRepository extends JpaRepository<ParkingPricing, Long> {

    /** Effective pricing rows for a location at a point in time. */
    @Query("""
            select p from ParkingPricing p
            where p.location.id = :locationId
              and p.effectiveFrom <= :at
              and (p.effectiveUntil is null or p.effectiveUntil > :at)
            order by p.vehicleType, p.pricingType, p.effectiveFrom desc
            """)
    List<ParkingPricing> findEffective(UUID locationId, LocalDateTime at);
}
