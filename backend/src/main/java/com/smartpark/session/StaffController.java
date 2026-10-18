package com.smartpark.session;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Security desk: verify QR, record entry/exit, watch live sessions. */
@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
public class StaffController {

    private final CheckinService checkinService;

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@AuthenticationPrincipal AuthUser staff,
                                    @RequestBody StaffDto.ScanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                checkinService.verify(staff.getId(), request), "Reservation verified."));
    }

    @PostMapping("/check-in")
    public ResponseEntity<?> checkIn(@AuthenticationPrincipal AuthUser staff,
                                     @RequestBody StaffDto.ScanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                checkinService.checkIn(staff.getId(), request), "Vehicle checked in."));
    }

    @PostMapping("/check-out")
    public ResponseEntity<?> checkOut(@AuthenticationPrincipal AuthUser staff,
                                      @RequestBody StaffDto.ScanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                checkinService.checkOut(staff.getId(), request), "Vehicle checked out."));
    }

    @GetMapping("/active-sessions")
    public ResponseEntity<?> activeSessions(@AuthenticationPrincipal AuthUser staff,
                                            @RequestParam(required = false) UUID locationId) {
        return ResponseEntity.ok(ApiResponse.ok(
                checkinService.activeSessions(staff.getId(), locationId), "Active sessions loaded."));
    }
}
