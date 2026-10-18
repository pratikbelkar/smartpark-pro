package com.smartpark.owner;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Owner console: dashboard, earnings, exports, reviews, staff, settings. */
@RestController
@RequestMapping("/api/v1/owner")
@RequiredArgsConstructor
public class OwnerConsoleController {

    private final OwnerDashboardService dashboardService;
    private final OwnerStaffService staffService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.dashboard(user.getId()), "Dashboard loaded."));
    }

    @GetMapping("/earnings")
    public ResponseEntity<?> earnings(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.earnings(user.getId()), "Earnings loaded."));
    }

    @GetMapping(value = "/bookings/export", produces = "text/csv")
    public ResponseEntity<String> export(@AuthenticationPrincipal AuthUser user,
                                         @RequestParam(required = false) UUID locationId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"owner-bookings.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(dashboardService.bookingsCsv(user.getId(), locationId, from, to));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(@AuthenticationPrincipal AuthUser user,
                                           @RequestBody Map<String, String> fields) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.updateProfile(user.getId(), fields), "Profile updated."));
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> reviews(@AuthenticationPrincipal AuthUser user,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                dashboardService.reviews(user.getId(), pageable), "Reviews loaded."));
    }

    // ----------------------------------------------------------------- staff

    @GetMapping("/staff")
    public ResponseEntity<?> staff(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.listStaff(user.getId()), "Staff loaded."));
    }

    @PostMapping("/staff")
    public ResponseEntity<?> grantStaff(@AuthenticationPrincipal AuthUser user,
                                        @RequestBody GrantStaffRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                staffService.grantStaff(user.getId(), request.getEmail()), "Staff access granted."));
    }

    @GetMapping("/staff/shifts")
    public ResponseEntity<?> shifts(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.listShifts(user.getId()), "Shifts loaded."));
    }

    @PostMapping("/staff/shifts")
    public ResponseEntity<?> createShift(@AuthenticationPrincipal AuthUser user,
                                         @RequestBody ShiftRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(staffService.createShift(user.getId(),
                request.getStaffUserId(), request.getLocationId(),
                request.getStartsAt(), request.getEndsAt()), "Shift rostered."));
    }

    @DeleteMapping("/staff/shifts/{id}")
    public ResponseEntity<?> deleteShift(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        staffService.deleteShift(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Shift removed."));
    }

    @Data
    public static class GrantStaffRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        private String email;
    }

    @Data
    public static class ShiftRequest {
        private UUID staffUserId;
        private UUID locationId;
        private LocalDateTime startsAt;
        private LocalDateTime endsAt;
    }
}
