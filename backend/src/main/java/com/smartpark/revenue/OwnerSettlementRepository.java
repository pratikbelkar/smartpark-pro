package com.smartpark.revenue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OwnerSettlementRepository extends JpaRepository<OwnerSettlement, Long> {

    java.util.List<OwnerSettlement> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
