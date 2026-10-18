package com.smartpark.revenue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface OwnerLedgerEntryRepository extends JpaRepository<OwnerLedgerEntry, Long> {

    @Query("select coalesce(sum(e.amountPaise), 0) from OwnerLedgerEntry e where e.owner.id = :ownerId")
    long balanceOf(UUID ownerId);

    @Query("select coalesce(sum(e.amountPaise), 0) from OwnerLedgerEntry e where e.owner.id = :ownerId and e.createdAt >= :since")
    long sumSince(UUID ownerId, java.time.LocalDateTime since);

    java.util.List<OwnerLedgerEntry> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
