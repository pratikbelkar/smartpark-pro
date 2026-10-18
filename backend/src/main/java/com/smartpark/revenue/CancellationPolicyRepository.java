package com.smartpark.revenue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CancellationPolicyRepository extends JpaRepository<CancellationPolicy, Long> {

    List<CancellationPolicy> findByLocationIdOrderByHoursBeforeStartDesc(UUID locationId);

    List<CancellationPolicy> findByLocationIsNullOrderByHoursBeforeStartDesc();
}
