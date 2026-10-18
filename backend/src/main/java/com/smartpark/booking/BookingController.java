package com.smartpark.booking;

import com.smartpark.common.ApiResponse;
import com.smartpark.qr.QrTicketService;
import com.smartpark.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer booking flow: quote → hold → (pay) → cancel / extend. */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final QrTicketService qrTicketService;

    @PostMapping("/quote")
    public ResponseEntity<?> quote(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody BookingDto.SlotWindowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.quote(user.getId(), request), "Price calculated."));
    }

    @PostMapping("/hold")
    public ResponseEntity<?> hold(@AuthenticationPrincipal AuthUser user,
                                  @Valid @RequestBody BookingDto.SlotWindowRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.hold(user.getId(), request), "Slot held. Complete payment before expiry."));
    }

    @GetMapping("/my")
    public ResponseEntity<?> mine(@AuthenticationPrincipal AuthUser user,
                                  @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.myBookings(user.getId(), pageable), "Bookings loaded."));
    }

    @GetMapping("/{reference}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthUser user, @PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok(bookingService.getMine(user.getId(), reference), "Booking loaded."));
    }

    @PostMapping("/{reference}/cancel")
    public ResponseEntity<?> cancel(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable String reference,
                                    @RequestBody(required = false) BookingDto.CancelRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.cancel(user.getId(), reference, reason), "Booking cancelled."));
    }

    @PostMapping("/{reference}/extend")
    public ResponseEntity<?> extend(@AuthenticationPrincipal AuthUser user,
                                    @PathVariable String reference,
                                    @Valid @RequestBody BookingDto.ExtendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                bookingService.extend(user.getId(), reference, request.getNewEnd()), "Booking extended."));
    }

    @GetMapping("/{reference}/qr")
    public ResponseEntity<?> qr(@AuthenticationPrincipal AuthUser user, @PathVariable String reference) {
        return ResponseEntity.ok(ApiResponse.ok(
                qrTicketService.ticket(user.getId(), reference), "QR pass ready."));
    }

    @GetMapping("/{reference}/qr/png")
    public ResponseEntity<byte[]> qrPng(@AuthenticationPrincipal AuthUser user, @PathVariable String reference) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition",
                        "inline; filename=\"%s-qr.png\"".formatted(reference.toUpperCase()))
                .body(qrTicketService.ticketPng(user.getId(), reference));
    }
}
