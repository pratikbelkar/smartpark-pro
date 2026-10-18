package com.smartpark.parking;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingLocationRepository extends JpaRepository<ParkingLocation, UUID> {

    Optional<ParkingLocation> findByIdAndDeletedAtIsNull(UUID id);

    List<ParkingLocation> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

    @Query("""
            select l from ParkingLocation l
            where l.deletedAt is null
              and l.operationalStatus = com.smartpark.parking.LocationStatus.OPEN
            """)
    Page<ParkingLocation> findPublic(Pageable pageable);

    @Query("""
            select l from ParkingLocation l
            where l.deletedAt is null
              and l.operationalStatus = com.smartpark.parking.LocationStatus.OPEN
              and lower(l.city) like lower(concat('%', :city, '%'))
            """)
    Page<ParkingLocation> searchPublic(String city, Pageable pageable);

    Page<ParkingLocation> findByDeletedAtIsNull(Pageable pageable);
}
