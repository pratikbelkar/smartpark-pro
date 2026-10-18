package com.smartpark.booking;

/** Payment rail state. Never used as occupancy state. */
public enum PaymentStatus {
    CREATED,
    PENDING,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    PARTIALLY_REFUNDED
}
