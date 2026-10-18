package com.smartpark.revenue;

import com.smartpark.booking.ParkingBooking;
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
@Table(name = "owner_ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class OwnerLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private ParkingOwner owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private ParkingBooking booking;

    /** BOOKING_CREDIT, COMMISSION_DEBIT, REFUND_DEBIT, ADJUSTMENT_*, SETTLEMENT_DEBIT. */
    @Column(nullable = false, length = 32)
    private String entryType;

    /** Signed paise. */
    @Column(nullable = false)
    private long amountPaise;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
