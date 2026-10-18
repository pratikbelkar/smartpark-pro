package com.smartpark.parking;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Owner back office: onboarding, locations, structure, slots, pricing. */
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerParkingController {

    private final OwnerService ownerService;
    private final ParkingService parkingService;

    @PostMapping("/onboard")
    public ResponseEntity<?> onboard(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody ParkingDto.OnboardRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                ownerService.onboard(user.getId(), request),
                "Application received. The platform team will verify your business."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> myProfile(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(ownerService.myProfile(user.getId()), "Owner profile loaded."));
    }

    // ------------------------------------------------------------- locations

    @GetMapping("/parking-locations")
    public ResponseEntity<?> myLocations(@AuthenticationPrincipal AuthUser user) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(parkingService.myLocations(owner), "Locations loaded."));
    }

    @PostMapping("/parking-locations")
    public ResponseEntity<?> createLocation(@AuthenticationPrincipal AuthUser user,
                                            @Valid @RequestBody ParkingDto.LocationRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.createLocation(owner, request), "Parking location created."));
    }

    @PutMapping("/parking-locations/{id}")
    public ResponseEntity<?> updateLocation(@AuthenticationPrincipal AuthUser user,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody ParkingDto.LocationRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.updateLocation(owner, id, request), "Parking location updated."));
    }

    @DeleteMapping("/parking-locations/{id}")
    public ResponseEntity<?> deleteLocation(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        parkingService.deleteLocation(owner, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Parking location removed."));
    }

    // --------------------------------------------------------------- floors

    @GetMapping("/parking-locations/{id}/floors")
    public ResponseEntity<?> floors(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(parkingService.listFloors(owner, id), "Floors loaded."));
    }

    @PostMapping("/parking-locations/{id}/floors")
    public ResponseEntity<?> createFloor(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody ParkingDto.FloorRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.createFloor(owner, id, request), "Floor created."));
    }

    @GetMapping("/floors/{floorId}/zones")
    public ResponseEntity<?> zones(@AuthenticationPrincipal AuthUser user, @PathVariable UUID floorId) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(parkingService.listZones(owner, floorId), "Zones loaded."));
    }

    @PostMapping("/zones")
    public ResponseEntity<?> createZone(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody ParkingDto.ZoneRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.createZone(owner, request), "Zone created."));
    }

    // ---------------------------------------------------------------- slots

    @GetMapping("/parking-locations/{id}/slots")
    public ResponseEntity<?> slots(@AuthenticationPrincipal AuthUser user,
                                   @PathVariable UUID id,
                                   @RequestParam(required = false) String vehicleType) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.listSlots(owner, id, vehicleType), "Slots loaded."));
    }

    @PostMapping("/parking-locations/{id}/slots")
    public ResponseEntity<?> createSlot(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable UUID id,
                                        @Valid @RequestBody ParkingDto.SlotRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.createSlot(owner, id, request), "Slot created."));
    }

    @PostMapping("/parking-locations/{id}/slots/bulk")
    public ResponseEntity<?> bulkSlots(@AuthenticationPrincipal AuthUser user,
                                       @PathVariable UUID id,
                                       @Valid @RequestBody ParkingDto.BulkSlotRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.bulkCreateSlots(owner, id, request), "Slots created."));
    }

    @PutMapping("/slots/{slotId}")
    public ResponseEntity<?> updateSlot(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable UUID slotId,
                                        @Valid @RequestBody ParkingDto.SlotRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.updateSlot(owner, slotId, request), "Slot updated."));
    }

    @PostMapping("/slots/{slotId}/status")
    public ResponseEntity<?> slotStatus(@AuthenticationPrincipal AuthUser user,
                                        @PathVariable UUID slotId,
                                        @Valid @RequestBody ParkingDto.SlotStatusRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.changeSlotStatus(owner, slotId, request), "Slot status updated."));
    }

    // --------------------------------------------------------------- pricing

    @GetMapping("/parking-locations/{id}/pricing")
    public ResponseEntity<?> pricing(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(parkingService.listPricing(owner, id), "Pricing loaded."));
    }

    @PostMapping("/parking-locations/{id}/pricing")
    public ResponseEntity<?> savePricing(@AuthenticationPrincipal AuthUser user,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody ParkingDto.PricingRequest request) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.savePricing(owner, id, request), "Pricing updated."));
    }
}
