package com.smartpark.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Short> {

    Optional<SubscriptionPlan> findByPlanCode(String planCode);
}
