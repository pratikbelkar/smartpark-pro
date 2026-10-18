package com.smartpark.booking;

import com.smartpark.notification.NotificationRepository;
import com.smartpark.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Minute sweeps. All transitions are idempotent (status-guarded) so overlapping
 * runs and restarts are safe. Checked-in sessions are never touched here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingLifecycleService {

    private final BookingHoldRepository holdRepository;
    private final ParkingBookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    /** Expire unpaid holds (+ their PENDING_PAYMENT bookings) and send start reminders. */
    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void sweep() {
        LocalDateTime now = LocalDateTime.now();
        expireHolds(now);
        expireUnpaidBookings(now);
        markNoShows(now);
        sendStartReminders(now);
    }

    void expireHolds(LocalDateTime now) {
        List<BookingHold> expired = holdRepository.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);
        for (BookingHold hold : expired) {
            hold.setStatus(HoldStatus.EXPIRED);
        }
        if (!expired.isEmpty()) {
            log.info("Expired {} booking holds.", expired.size());
        }
    }

    void expireUnpaidBookings(LocalDateTime now) {
        // PENDING_PAYMENT bookings whose hold lapsed never become payable inventory.
        List<ParkingBooking> dead = bookingRepository.findUnpaidWithExpiredHold();
        for (ParkingBooking booking : dead) {
            booking.setBookingStatus(BookingStatus.EXPIRED);
            notificationService.notify(booking.getCustomer(), "BOOKING_EXPIRED",
                    "Booking %s expired".formatted(booking.getBookingReference()),
                    "The payment window lapsed and the slot was released.");
        }
        if (!dead.isEmpty()) {
            log.info("Expired {} unpaid bookings.", dead.size());
        }
    }

    void markNoShows(LocalDateTime now) {
        bookingRepository.findConfirmedAwaitingEntry().stream()
                .filter(b -> now.isAfter(b.getStartTime().plusMinutes(graceOf(b))))
                .forEach(b -> {
                    b.setBookingStatus(BookingStatus.NO_SHOW);
                    notificationService.notify(b.getCustomer(), "NO_SHOW",
                            "Marked as no-show: %s".formatted(b.getBookingReference()),
                            "The slot was released after the grace period. Contact support for help.");
                    log.info("Booking {} marked NO_SHOW.", b.getBookingReference());
                });
    }

    void sendStartReminders(LocalDateTime now) {
        bookingRepository.findConfirmedAwaitingEntry().stream()
                .filter(b -> !b.getStartTime().isAfter(now.plusMinutes(30))
                        && b.getStartTime().isAfter(now))
                .filter(b -> !notificationRepository.existsByUserIdAndNotificationTypeAndCreatedAtAfter(
                        b.getCustomer().getId(), "BOOKING_REMINDER", now.minusHours(2)))
                .forEach(b -> notificationService.notify(b.getCustomer(), "BOOKING_REMINDER",
                        "Parking starts soon: slot %s".formatted(b.getSlot().getSlotNumber()),
                        "%s at %s. Your QR pass is ready in My Bookings."
                                .formatted(b.getLocation().getParkingName(), b.getStartTime())));
    }

    private int graceOf(ParkingBooking booking) {
        try {
            Object grace = booking.getPricingSnapshot().getOrDefault("graceMinutes", 15);
            return Integer.parseInt(String.valueOf(grace));
        } catch (Exception ex) {
            return 15;
        }
    }

    /**
     * Admin/ops hook to run the sweep on demand (used by tests too).
     * Carries its own transaction because it calls the sweep steps directly,
     * bypassing the scheduled proxy.
     */
    @Transactional
    public void runOnce() {
        LocalDateTime now = LocalDateTime.now();
        expireHolds(now);
        expireUnpaidBookings(now);
        markNoShows(now);
        sendStartReminders(now);
    }
}
