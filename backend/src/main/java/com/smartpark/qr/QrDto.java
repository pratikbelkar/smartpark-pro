package com.smartpark.qr;

import lombok.Data;

import java.time.LocalDateTime;

public final class QrDto {

    private QrDto() {
    }

    @Data
    public static class TicketResponse {
        private String bookingReference;
        private String bookingStatus;
        /** Opaque token encoded in the QR image. */
        private String token;
        private String dataUri;
        private LocalDateTime expiresAt;
        private String customerName;
        private String vehicleNumber;
        private String locationName;
        private String slotNumber;
        private String floorName;
        private String zoneName;
        private LocalDateTime start;
        private LocalDateTime end;
    }
}
