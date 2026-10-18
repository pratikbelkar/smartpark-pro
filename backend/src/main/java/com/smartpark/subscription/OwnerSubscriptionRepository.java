package com.smartpark.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OwnerSubscriptionRepository extends JpaRepository<OwnerSubscription, Long> {

    List<OwnerSubscription> findByOwnerIdOrderByStartsAtDesc(UUID ownerId);
}
