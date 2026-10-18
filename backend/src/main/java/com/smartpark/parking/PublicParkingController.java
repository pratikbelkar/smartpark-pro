package com.smartpark.parking;

import com.smartpark.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.UUID;

/** Public browsing: search locations, view details, check live availability. */
@RestController
@RequestMapping("/api/v1/parking-locations")
@RequiredArgsConstructor
public class PublicParkingController {

    private final ParkingService parkingService;

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.search(city, vehicleType, start, end, pageable), "Parking locations loaded."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(parkingService.publicDetail(id), "Parking details loaded."));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<?> availability(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String vehicleType) {
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.availability(id, start, end, vehicleType), "Availability loaded."));
    }
}
