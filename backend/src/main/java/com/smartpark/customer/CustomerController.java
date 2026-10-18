package com.smartpark.customer;

import com.smartpark.booking.BookingService;
import com.smartpark.common.ApiResponse;
import com.smartpark.notification.NotificationService;
import com.smartpark.security.AuthUser;
import com.smartpark.vehicle.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in customer home: active + upcoming bookings, vehicles, unread count. */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final BookingService bookingService;
    private final VehicleService vehicleService;
    private final NotificationService notificationService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal AuthUser user) {
        var bookings = bookingService.myBookings(user.getId(), PageRequest.of(0, 50)).getContent();
        var active = bookings.stream()
                .filter(b -> b.getBookingStatus().equals("CHECKED_IN"))
                .toList();
        var upcoming = bookings.stream()
                .filter(b -> b.getBookingStatus().equals("CONFIRMED")
                        || b.getBookingStatus().equals("PENDING_PAYMENT"))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(ApiResponse.map(
                "active", active,
                "upcoming", upcoming,
                "history", bookings.stream()
                        .filter(b -> !b.getBookingStatus().equals("CHECKED_IN")
                                && !b.getBookingStatus().equals("CONFIRMED")
                                && !b.getBookingStatus().equals("PENDING_PAYMENT"))
                        .limit(10).toList(),
                "vehicles", vehicleService.list(user.getId()),
                "unreadNotifications", notificationService.unreadCount(user.getId())), "Dashboard loaded."));
    }
}
