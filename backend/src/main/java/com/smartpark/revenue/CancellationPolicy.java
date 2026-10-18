package com.smartpark.revenue;

import com.smartpark.parking.ParkingLocation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

@Entity
@Table(name = "cancellation_policies")
@Getter
@Setter
@NoArgsConstructor
public class CancellationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null = platform default policy tier. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_location_id")
    private ParkingLocation location;

    @Column(nullable = false)
    private int hoursBeforeStart;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal refundPercent;
}
