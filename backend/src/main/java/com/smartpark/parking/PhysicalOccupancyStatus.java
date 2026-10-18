package com.smartpark.parking;

/**
 * Sensor/observed state only. Never used for booking availability —
 * that comes from holds + bookings over the requested time window.
 */
public enum PhysicalOccupancyStatus {
    FREE,
    OCCUPIED
}
