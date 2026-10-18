package com.smartpark.session;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

public final class StaffDto {

    private StaffDto() {
    }

    @Data
    public static class ScanRequest {
        /** Raw QR token. One of qrToken / bookingReference / vehicleNumber is required. */
        private String qrToken;
        private String bookingReference;
        @Size(max = 20)
        private String vehicleNumber;
    }

    @Data
    public static class VerifyResponse {
        private String bookingReference;
        private String bookingStatus;
        private String paymentStatus;
        private String customerName;
        private String vehicleNumber;
        private UUID locationId;
        private String locationName;
        private String slotNumber;
        private String floorName;
        private String zoneName;
        private LocalDateTime start;
        private LocalDateTime end;
        private LocalDateTime actualEntryTime;
        private boolean canCheckIn;
        private String checkInBlockReason;
        private boolean canCheckOut;
        private String checkOutBlockReason;
    }

    @Data
    public static class SessionResponse {
        private UUID sessionId;
        private String bookingReference;
        private String slotNumber;
        private LocalDateTime entryTime;
        private LocalDateTime exitTime;
        private Integer durationMinutes;
        private long extraPaise;
        private String sessionStatus;
        private String bookingStatus;
        private String paymentStatus;
    }
}
