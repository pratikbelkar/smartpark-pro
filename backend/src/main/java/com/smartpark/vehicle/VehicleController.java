package com.smartpark.vehicle;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(vehicleService.list(user.getId()), "Vehicles loaded."));
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody VehicleDto.VehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                vehicleService.create(user.getId(), request), "Vehicle saved."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody VehicleDto.VehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                vehicleService.update(user.getId(), id, request), "Vehicle updated."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        vehicleService.delete(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Vehicle removed."));
    }
}
