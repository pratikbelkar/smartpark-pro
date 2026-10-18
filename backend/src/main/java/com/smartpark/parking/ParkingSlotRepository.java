package com.smartpark.parking;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {

    /** Pessimistic lock: serializes concurrent hold/confirm attempts on one slot. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSlot s where s.id = :id")
    Optional<ParkingSlot> findByIdForUpdate(UUID id);

    List<ParkingSlot> findByLocationIdOrderBySlotNumberAsc(UUID locationId);

    List<ParkingSlot> findByLocationIdAndVehicleTypeAndOperationalStatusOrderBySlotNumberAsc(
            UUID locationId, VehicleType vehicleType, SlotOperationalStatus status);

    long countByLocationIdAndVehicleTypeAndOperationalStatus(
            UUID locationId, VehicleType vehicleType, SlotOperationalStatus status);

    boolean existsByLocationIdAndSlotNumberIgnoreCase(UUID locationId, String slotNumber);

    List<ParkingSlot> findByFloorIdOrderBySlotNumberAsc(UUID floorId);
}
