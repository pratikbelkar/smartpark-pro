package com.smartpark.revenue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PlatformFeeRepository extends JpaRepository<PlatformFee, Long> {

    @Query("""
            select f from PlatformFee f
            where f.feeKey = :key
              and f.effectiveFrom <= :at
              and (f.effectiveUntil is null or f.effectiveUntil > :at)
            order by f.effectiveFrom desc
            """)
    Optional<PlatformFee> findEffective(String key, LocalDateTime at);
}
