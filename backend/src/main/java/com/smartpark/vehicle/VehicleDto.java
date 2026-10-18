package com.smartpark.vehicle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

public final class VehicleDto {

    private VehicleDto() {
    }

    @Data
    public static class VehicleRequest {
        @NotBlank(message = "Vehicle number is required")
        @Size(max = 20, message = "Vehicle number must be at most 20 characters")
        private String vehicleNumber;

        @NotBlank(message = "Vehicle type is required")
        @Pattern(regexp = "^(TWO_WHEELER|CAR)$", message = "Vehicle type must be TWO_WHEELER or CAR")
        private String vehicleType;

        @Size(max = 80)
        private String vehicleModel;

        @Size(max = 40)
        private String vehicleColor;

        private boolean defaultVehicle = false;
    }

    @Data
    public static class VehicleResponse {
        private UUID id;
        private String vehicleNumber;
        private String vehicleType;
        private String vehicleModel;
        private String vehicleColor;
        private boolean defaultVehicle;
    }
}
