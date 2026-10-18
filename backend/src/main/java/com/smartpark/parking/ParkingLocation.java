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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "parking_locations")
@Getter
@Setter
@NoArgsConstructor
public class ParkingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private ParkingOwner owner;

    @Column(nullable = false, length = 160)
    private String parkingName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String fullAddress;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(length = 80)
    private String state;

    @Column(length = 16)
    private String postalCode;

    private Double latitude;
    private Double longitude;
    private Double entranceLatitude;
    private Double entranceLongitude;

    @Column(nullable = false)
    private int totalCapacity;

    private LocalTime openingTime;
    private LocalTime closingTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LocationStatus operationalStatus = LocationStatus.OPEN;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
