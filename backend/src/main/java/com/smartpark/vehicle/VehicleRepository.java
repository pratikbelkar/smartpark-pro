package com.smartpark.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<Vehicle> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndVehicleNumberIgnoreCase(UUID userId, String vehicleNumber);
}
