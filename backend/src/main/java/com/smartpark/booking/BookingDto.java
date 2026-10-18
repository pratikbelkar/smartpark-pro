package com.smartpark.booking;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public final class BookingDto {

    private BookingDto() {
    }

    @Data
    public static class SlotWindowRequest {
        @NotNull(message = "Parking location is required")
        private UUID locationId;

        /** Omit for automatic allocation (lowest free slot number). */
        private UUID slotId;

        private String vehicleType = "CAR";

        private UUID vehicleId;

        @NotNull(message = "Start time is required")
        private LocalDateTime start;

        @NotNull(message = "End time is required")
        private LocalDateTime end;
    }

    @Data
    public static class QuoteResponse {
        private UUID locationId;
        private UUID slotId;
        private String slotNumber;
        private String vehicleType;
        private LocalDateTime start;
        private LocalDateTime end;
        private long billableHours;
        private long parkingPaise;
        private long conveniencePaise;
        private long taxPaise;
        private long totalPaise;
        private int holdTtlMinutes;
        private Map<String, Object> pricingSnapshot;
    }

    @Data
    public static class HoldResponse {
        private UUID holdId;
        private String bookingReference;
        private UUID slotId;
        private String slotNumber;
        private LocalDateTime start;
        private LocalDateTime end;
        private LocalDateTime expiresAt;
        private long totalPaise;
    }

    @Data
    public static class BookingResponse {
        private UUID id;
        private String bookingReference;
        private String bookingStatus;
        private String paymentStatus;
        private UUID locationId;
        private String locationName;
        private String slotNumber;
        private String floorName;
        private String zoneName;
        private String vehicleNumber;
        private LocalDateTime start;
        private LocalDateTime end;
        private LocalDateTime actualEntryTime;
        private LocalDateTime actualExitTime;
        private long parkingPaise;
        private long conveniencePaise;
        private long taxPaise;
        private long totalPaise;
        private LocalDateTime createdAt;
    }

    @Data
    public static class CancelRequest {
        private String reason;
    }

    @Data
    public static class CancelResponse {
        private String bookingReference;
        private String bookingStatus;
        private long refundPaise;
        private String refundStatus;
    }

    @Data
    public static class ExtendRequest {
        @NotNull(message = "New end time is required")
        private LocalDateTime newEnd;
    }

    @Data
    public static class ExtendResponse {
        private String bookingReference;
        private LocalDateTime newEnd;
        private long extraDuePaise;
        private long totalPaise;
        private String paymentStatus;
    }
}
