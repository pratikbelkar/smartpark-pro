package com.smartpark.parking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParkingZoneRepository extends JpaRepository<ParkingZone, UUID> {

    List<ParkingZone> findByFloorId(UUID floorId);
}
