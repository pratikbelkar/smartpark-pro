package com.smartpark.parking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParkingImageRepository extends JpaRepository<ParkingImage, Long> {

    List<ParkingImage> findByLocationIdOrderBySortOrderAsc(UUID locationId);

    void deleteByLocationId(UUID locationId);
}
