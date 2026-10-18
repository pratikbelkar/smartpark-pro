package com.smartpark.admin;

import com.smartpark.booking.BookingService;
import com.smartpark.common.ApiResponse;
import com.smartpark.payment.PaymentService;
import com.smartpark.parking.OwnerService;
import com.smartpark.parking.ParkingDto;
import com.smartpark.parking.ParkingService;
import com.smartpark.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
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

/** Super-admin operations: owner approvals, location oversight. */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final OwnerService ownerService;
    private final ParkingService parkingService;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final AdminDashboardService dashboardService;
    private final AdminOpsService opsService;

    @GetMapping("/owners")
    public ResponseEntity<?> owners(@RequestParam(required = false) String status,
                                    @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(ownerService.list(status, pageable), "Owners loaded."));
    }

    @PutMapping("/owners/{id}/status")
    public ResponseEntity<?> ownerStatus(@AuthenticationPrincipal AuthUser admin,
                                         @PathVariable UUID id,
                                         @Valid @RequestBody ParkingDto.OwnerStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                ownerService.updateStatus(id, request.getStatus(), admin), "Owner status updated."));
    }

    @GetMapping("/parking-locations")
    public ResponseEntity<?> locations(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(parkingService.listAll(pageable), "Locations loaded."));
    }

    @PutMapping("/parking-locations/{id}/status")
    public ResponseEntity<?> locationStatus(@PathVariable UUID id,
                                            @Valid @RequestBody ParkingDto.LocationStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                parkingService.updateOperationalStatus(id, request.getStatus()), "Location status updated."));
    }

    @GetMapping("/bookings")
    public ResponseEntity<?> bookings(@RequestParam(required = false) String status,
                                      @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.adminBookings(status, pageable), "Bookings loaded."));
    }

    @GetMapping("/refunds")
    public ResponseEntity<?> refunds() {
        return ResponseEntity.ok(ApiResponse.ok(paymentService.listRefunds(), "Refunds loaded."));
    }

    @PostMapping("/refunds/process")
    public ResponseEntity<?> processRefunds() {
        int processed = paymentService.processPendingRefunds();
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponse.map("processed", processed), "Refund queue processed."));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.dashboard(), "Dashboard loaded."));
    }

    @GetMapping("/revenue")
    public ResponseEntity<?> revenue() {
        return ResponseEntity.ok(ApiResponse.ok(dashboardService.revenue(), "Revenue loaded."));
    }

    @GetMapping("/users")
    public ResponseEntity<?> users(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String role,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.users(search, role, pageable), "Users loaded."));
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<?> userStatus(@AuthenticationPrincipal AuthUser admin,
                                        @PathVariable UUID id,
                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(
                opsService.updateUserStatus(id, body.getOrDefault("status", ""), admin.getId()),
                "User status updated."));
    }

    @GetMapping("/payments")
    public ResponseEntity<?> payments(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.payments(pageable), "Payments loaded."));
    }

    @GetMapping("/reviews")
    public ResponseEntity<?> reviews(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.reviews(pageable), "Reviews loaded."));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> auditLogs(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.auditLogs(pageable), "Audit logs loaded."));
    }

    @GetMapping("/settings/fees")
    public ResponseEntity<?> fees() {
        return ResponseEntity.ok(ApiResponse.ok(opsService.fees(), "Fee settings loaded."));
    }

    @PutMapping("/settings/fees")
    public ResponseEntity<?> updateFees(@AuthenticationPrincipal AuthUser admin,
                                        @RequestBody FeeRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.updateFees(
                request.getConveniencePaise(), request.getCommissionPercent(), admin.getId()),
                "Fee settings updated."));
    }

    @GetMapping("/settlements")
    public ResponseEntity<?> settlements(@RequestParam(required = false) UUID ownerId) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.settlements(ownerId), "Settlements loaded."));
    }

    @PostMapping("/settlements")
    public ResponseEntity<?> createSettlement(@AuthenticationPrincipal AuthUser admin,
                                              @RequestBody SettlementRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.createSettlement(
                request.getOwnerId(), request.getAmountPaise(), admin.getId()), "Settlement created."));
    }

    @PostMapping("/settlements/{id}/settle")
    public ResponseEntity<?> settle(@AuthenticationPrincipal AuthUser admin, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                opsService.settleSettlement(id, admin.getId()), "Settlement marked settled."));
    }

    @GetMapping("/support")
    public ResponseEntity<?> tickets(@RequestParam(required = false) String status,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.tickets(status, pageable), "Tickets loaded."));
    }

    @PutMapping("/support/{id}")
    public ResponseEntity<?> ticket(@AuthenticationPrincipal AuthUser admin,
                                    @PathVariable Long id,
                                    @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.updateTicket(
                id, body.getOrDefault("status", ""), admin.getId()), "Ticket updated."));
    }

    @GetMapping("/subscription-plans")
    public ResponseEntity<?> plans() {
        return ResponseEntity.ok(ApiResponse.ok(opsService.plans(), "Plans loaded."));
    }

    @PostMapping("/owners/{id}/subscription")
    public ResponseEntity<?> assignPlan(@AuthenticationPrincipal AuthUser admin,
                                        @PathVariable UUID id,
                                        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.ok(opsService.assignPlan(
                id, body.getOrDefault("planCode", ""), admin.getId()), "Plan assigned."));
    }

    @GetMapping(value = "/reports/bookings.csv", produces = "text/csv")
    public ResponseEntity<String> bookingsCsv(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"bookings-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(opsService.bookingsCsv(from, to, status));
    }

    @lombok.Data
    public static class FeeRequest {
        private Long conveniencePaise;
        private java.math.BigDecimal commissionPercent;
    }

    @lombok.Data
    public static class SettlementRequest {
        private UUID ownerId;
        private long amountPaise;
    }
}
