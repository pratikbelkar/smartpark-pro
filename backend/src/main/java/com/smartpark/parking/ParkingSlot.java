package com.smartpark.parking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "parking_slots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parking_location_id", "slot_number"}))
@Getter
@Setter
@NoArgsConstructor
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_location_id", nullable = false)
    private ParkingLocation location;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private ParkingFloor floor;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private ParkingZone zone;

    @Column(nullable = false, length = 24)
    private String slotNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PhysicalOccupancyStatus physicalOccupancyStatus = PhysicalOccupancyStatus.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SlotOperationalStatus operationalStatus = SlotOperationalStatus.AVAILABLE;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
