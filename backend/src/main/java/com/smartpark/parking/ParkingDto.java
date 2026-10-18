package com.smartpark.parking;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ParkingDto {

    private ParkingDto() {
    }

    // ---------------------------------------------------------------- owner

    @Data
    public static class OnboardRequest {
        @NotBlank(message = "Business name is required")
        @Size(max = 160)
        private String businessName;
        private String businessEmail;
        @Size(max = 20)
        private String businessPhone;
        private String businessAddress;
    }

    @Data
    public static class OwnerResponse {
        private UUID id;
        private String businessName;
        private String businessEmail;
        private String businessPhone;
        private String businessAddress;
        private String verificationStatus;
        private LocalDateTime createdAt;
    }

    @Data
    public static class OwnerStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;
    }

    // ------------------------------------------------------------- location

    @Data
    public static class LocationRequest {
        @NotBlank(message = "Parking name is required")
        @Size(max = 160)
        private String parkingName;
        private String description;
        @NotBlank(message = "Address is required")
        private String fullAddress;
        @NotBlank(message = "City is required")
        @Size(max = 80)
        private String city;
        @Size(max = 80)
        private String state;
        @Size(max = 16)
        private String postalCode;
        private Double latitude;
        private Double longitude;
        private Double entranceLatitude;
        private Double entranceLongitude;
        @Min(value = 0, message = "Capacity cannot be negative")
        private int totalCapacity;
        private LocalTime openingTime;
        private LocalTime closingTime;
        private List<String> imageUrls = new ArrayList<>();
    }

    @Data
    public static class LocationStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;
    }

    @Data
    public static class LocationCard {
        private UUID id;
        private String parkingName;
        private String city;
        private String fullAddress;
        private Double latitude;
        private Double longitude;
        private String imageUrl;
        private String operationalStatus;
        /** Effective hourly rates in paise by vehicle type. */
        private Map<String, Long> hourlyRates;
        /** Free slots in the requested window (null when no window given). */
        private Long freeSlots;
    }

    @Data
    public static class LocationDetail {
        private UUID id;
        private String parkingName;
        private String description;
        private String fullAddress;
        private String city;
        private String state;
        private String postalCode;
        private Double latitude;
        private Double longitude;
        private Double entranceLatitude;
        private Double entranceLongitude;
        private int totalCapacity;
        private LocalTime openingTime;
        private LocalTime closingTime;
        private String operationalStatus;
        private List<String> imageUrls = new ArrayList<>();
        private Map<String, Long> hourlyRates;
        private Double averageRating;
        private long reviewCount;
    }

    // ------------------------------------------------------- floors / zones

    @Data
    public static class FloorRequest {
        @NotBlank(message = "Floor name is required")
        @Size(max = 40)
        private String floorName;
        private int floorNumber;
    }

    @Data
    public static class ZoneRequest {
        @NotNull(message = "Floor is required")
        private UUID floorId;
        @NotBlank(message = "Zone name is required")
        @Size(max = 40)
        private String zoneName;
    }

    @Data
    public static class FloorResponse {
        private UUID id;
        private String floorName;
        private int floorNumber;
    }

    @Data
    public static class ZoneResponse {
        private UUID id;
        private String zoneName;
        private String floorName;
    }

    // ---------------------------------------------------------------- slots

    @Data
    public static class SlotRequest {
        @NotNull(message = "Floor is required")
        private UUID floorId;
        @NotNull(message = "Zone is required")
        private UUID zoneId;
        @NotBlank(message = "Slot number is required")
        @Size(max = 24)
        private String slotNumber;
        @NotBlank(message = "Vehicle type is required")
        private String vehicleType;
    }

    @Data
    public static class BulkSlotRequest {
        @NotNull(message = "Floor is required")
        private UUID floorId;
        @NotNull(message = "Zone is required")
        private UUID zoneId;
        @NotBlank(message = "Vehicle type is required")
        private String vehicleType;
        @NotBlank(message = "Prefix is required, e.g. C")
        @Size(max = 4)
        private String prefix = "C";
        @Min(value = 0)
        private int startNumber = 1;
        @Min(value = 1, message = "Create at least 1 slot")
        @Max(value = 200, message = "Create at most 200 slots at a time")
        private int count;
    }

    @Data
    public static class SlotStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;
        @Size(max = 500)
        private String reason;
        private LocalDateTime endsAt;
    }

    @Data
    public static class SlotResponse {
        private UUID id;
        private String slotNumber;
        private String vehicleType;
        private String floorName;
        private String zoneName;
        private String operationalStatus;
        private String physicalOccupancyStatus;
    }

    // -------------------------------------------------------------- pricing

    @Data
    public static class PricingRequest {
        @NotBlank(message = "Vehicle type is required")
        private String vehicleType;
        private String pricingType = "HOURLY";
        /** Rupees (decimal allowed); converted to paise server-side. */
        @NotNull(message = "Hourly rate is required")
        @DecimalMin(value = "0.0", message = "Rate cannot be negative")
        private BigDecimal hourlyRateRupees;
        private BigDecimal dailyRateRupees;
        @Min(value = 0)
        private int gracePeriodMinutes = 15;
    }

    @Data
    public static class PricingResponse {
        private Long id;
        private String vehicleType;
        private String pricingType;
        private long hourlyRatePaise;
        private Long dailyRatePaise;
        private int gracePeriodMinutes;
        private LocalDateTime effectiveFrom;
        private LocalDateTime effectiveUntil;
    }

    // --------------------------------------------------------- availability

    @Data
    public static class SlotState {
        private final UUID slotId;
        private final String slotNumber;
        private final String state;
    }

    @Data
    public static class ZoneAvailability {
        private final UUID zoneId;
        private final String zoneName;
        private final List<SlotState> slots = new ArrayList<>();
    }

    @Data
    public static class FloorAvailability {
        private final UUID floorId;
        private final String floorName;
        private final List<ZoneAvailability> zones = new ArrayList<>();
    }

    @Data
    public static class AvailabilityResponse {
        private final UUID locationId;
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String vehicleType;
        private final long freeSlots;
        private final List<FloorAvailability> floors = new ArrayList<>();
    }
}
