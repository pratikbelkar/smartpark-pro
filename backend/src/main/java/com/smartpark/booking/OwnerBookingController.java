package com.smartpark.booking;

import com.smartpark.common.ApiResponse;
import com.smartpark.parking.OwnerService;
import com.smartpark.parking.ParkingOwner;
import com.smartpark.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Owner-scoped booking visibility (tenant-isolated). */
@RestController
@RequestMapping("/api/v1/owner/bookings")
@RequiredArgsConstructor
public class OwnerBookingController {

    private final OwnerService ownerService;
    private final BookingService bookingService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal AuthUser user,
                                  @RequestParam(required = false) UUID locationId,
                                  @PageableDefault(size = 20) Pageable pageable) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.ownerBookings(owner, locationId, pageable), "Bookings loaded."));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthUser user, @PathVariable String reference) {
        ParkingOwner owner = ownerService.requireApprovedOwner(user.getId());
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.ownerBooking(owner, reference), "Booking loaded."));
    }
}
