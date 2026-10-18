package com.smartpark.revenue;

import com.smartpark.parking.ParkingOwner;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "owner_settlements")
@Getter
@Setter
@NoArgsConstructor
public class OwnerSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private ParkingOwner owner;

    @Column(nullable = false, unique = true, length = 48)
    private String settlementReference;

    /** Paise. */
    @Column(nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 16)
    private String status = "PENDING";

    private LocalDateTime settledAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
