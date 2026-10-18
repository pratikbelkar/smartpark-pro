package com.smartpark.session;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.booking.PaymentStatus;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.notification.EmailService;
import com.smartpark.notification.NotificationService;
import com.smartpark.parking.PhysicalOccupancyStatus;
import com.smartpark.parking.ParkingPricing;
import com.smartpark.parking.ParkingPricingRepository;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.PricingType;
import com.smartpark.parking.VehicleType;
import com.smartpark.qr.QrPass;
import com.smartpark.qr.QrService;
import com.smartpark.staff.StaffShiftRepository;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gate operations: verify → check-in → check-out. Staff are scoped to the
 * locations of their currently active shifts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final QrService qrService;
    private final ParkingBookingRepository bookingRepository;
    private final ParkingSessionRepository sessionRepository;
    private final EntryExitLogRepository logRepository;
    private final ParkingPricingRepository pricingRepository;
    private final StaffShiftRepository shiftRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // ---------------------------------------------------------------- verify

    @Transactional(readOnly = true)
    public StaffDto.VerifyResponse verify(UUID staffId, StaffDto.ScanRequest request) {
        ParkingBooking booking = resolve(staffId, request);
        return describe(booking, presentedPlate(request));
    }

    // --------------------------------------------------------------- check-in

    @Transactional
    public StaffDto.SessionResponse checkIn(UUID staffId, StaffDto.ScanRequest request) {
        ParkingBooking booking = resolve(staffId, request);
        String plate = presentedPlate(request);
        StaffDto.VerifyResponse view = describe(booking, plate);
        if (!view.isCanCheckIn()) {
            throw checkInError(view);
        }
        matchVehicle(booking, plate);

        ParkingSession session = new ParkingSession();
        session.setBooking(booking);
        session.setSlot(booking.getSlot());
        session.setEntryTime(LocalDateTime.now());
        session.setSessionStatus("ACTIVE");
        session = sessionRepository.save(session);

        booking.setActualEntryTime(session.getEntryTime());
        booking.setBookingStatus(BookingStatus.CHECKED_IN);
        booking.getSlot().setPhysicalOccupancyStatus(PhysicalOccupancyStatus.OCCUPIED);

        writeLog(session, booking, staffId, "ENTRY",
                plate == null ? "QR check-in" : "QR check-in, plate " + plate);
        notificationService.notify(booking.getCustomer(), "CHECKED_IN",
                "Checked in: slot %s".formatted(booking.getSlot().getSlotNumber()),
                "Park in slot %s and enjoy your stay.".formatted(booking.getSlot().getSlotNumber()));
        log.info("Check-in {} at slot {}.", booking.getBookingReference(), booking.getSlot().getSlotNumber());
        return toSessionResponse(session);
    }

    // -------------------------------------------------------------- check-out

    @Transactional
    public StaffDto.SessionResponse checkOut(UUID staffId, StaffDto.ScanRequest request) {
        ParkingBooking booking = resolve(staffId, request);
        ParkingSession session = sessionRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> ApiException.conflict(ErrorCode.ALREADY_CHECKED_OUT,
                        "No active parking session for this booking."));
        if (!"ACTIVE".equals(session.getSessionStatus())) {
            throw ApiException.conflict(ErrorCode.ALREADY_CHECKED_OUT,
                    "This vehicle already checked out at %s.".formatted(session.getExitTime()));
        }

        LocalDateTime exit = LocalDateTime.now();
        long durationMin = Math.max(0, Duration.between(session.getEntryTime(), exit).toMinutes());
        long extraPaise = overstayCharge(booking, exit);

        session.setExitTime(exit);
        session.setDurationMinutes((int) Math.min(durationMin, Integer.MAX_VALUE));
        session.setExtraAmountPaise(extraPaise);
        session.setSessionStatus("COMPLETED");

        booking.setActualExitTime(exit);
        booking.setBookingStatus(BookingStatus.COMPLETED);
        if (extraPaise > 0) {
            booking.setPaymentStatus(PaymentStatus.PENDING);
        }
        booking.getSlot().setPhysicalOccupancyStatus(PhysicalOccupancyStatus.FREE);

        writeLog(session, booking, staffId, "EXIT",
                extraPaise > 0 ? "Overstay %s".formatted(money(extraPaise)) : "On-time exit");
        notificationService.notify(booking.getCustomer(), "CHECKED_OUT",
                "Checked out: %s".formatted(booking.getBookingReference()),
                extraPaise > 0
                        ? "Parking completed with %s overstay. Please settle it in My Bookings."
                                .formatted(money(extraPaise))
                        : "Thanks for parking with us. Receipt is available in My Bookings.");
        emailService.checkoutReceipt(booking.getCustomer().getEmail(), booking.getBookingReference(),
                "%d min".formatted(durationMin), extraPaise > 0 ? money(extraPaise) : null);
        log.info("Check-out {} ({} min, extra {}).", booking.getBookingReference(), durationMin, extraPaise);
        return toSessionResponse(session);
    }

    // ------------------------------------------------------- active sessions

    @Transactional(readOnly = true)
    public List<StaffDto.SessionResponse> activeSessions(UUID staffId, UUID locationId) {
        Set<UUID> scope = staffScope(staffId);
        return sessionRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getSessionStatus()))
                .filter(s -> scope.contains(s.getBooking().getLocation().getId()))
                .filter(s -> locationId == null || s.getBooking().getLocation().getId().equals(locationId))
                .map(this::toSessionResponse)
                .toList();
    }

    // --------------------------------------------------------------- internals

    private ParkingBooking resolve(UUID staffId, StaffDto.ScanRequest request) {
        Set<UUID> scope = staffScope(staffId);
        ParkingBooking booking = null;
        if (request.getQrToken() != null && !request.getQrToken().isBlank()) {
            QrPass pass = qrService.verifyToken(request.getQrToken());
            booking = pass.getBooking();
        } else if (request.getBookingReference() != null && !request.getBookingReference().isBlank()) {
            booking = bookingRepository.findByBookingReference(request.getBookingReference().trim().toUpperCase())
                    .orElseThrow(() -> ApiException.notFound("Booking not found."));
        } else if (request.getVehicleNumber() != null && !request.getVehicleNumber().isBlank()) {
            String plate = normalize(request.getVehicleNumber());
            List<ParkingBooking> matches = bookingRepository
                    .findByVehicle_VehicleNumberIgnoreCaseAndBookingStatusInOrderByStartTimeDesc(
                            plate, List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN));
            booking = matches.stream()
                    .filter(b -> scope.contains(b.getLocation().getId()))
                    .findFirst()
                    .orElseThrow(() -> ApiException.notFound(
                            "No active booking for vehicle " + plate + " at your location."));
        } else {
            throw ApiException.unprocessable("Provide a QR token, booking reference, or vehicle number.");
        }
        if (!scope.contains(booking.getLocation().getId())) {
            throw ApiException.forbidden("This booking belongs to another parking location.");
        }
        return booking;
    }

    private Set<UUID> staffScope(UUID staffId) {
        LocalDateTime now = LocalDateTime.now();
        Set<UUID> scope = shiftRepository
                .findByStaffIdAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(staffId, now, now).stream()
                .map(shift -> shift.getLocation().getId())
                .collect(Collectors.toSet());
        if (scope.isEmpty()) {
            throw ApiException.forbidden("You have no active shift. Contact your manager.");
        }
        return scope;
    }

    private StaffDto.VerifyResponse describe(ParkingBooking booking, String plate) {
        StaffDto.VerifyResponse view = new StaffDto.VerifyResponse();
        view.setBookingReference(booking.getBookingReference());
        view.setBookingStatus(booking.getBookingStatus().name());
        view.setPaymentStatus(booking.getPaymentStatus().name());
        view.setCustomerName(booking.getCustomer().getFullName());
        view.setVehicleNumber(booking.getVehicle() == null ? null : booking.getVehicle().getVehicleNumber());
        view.setLocationId(booking.getLocation().getId());
        view.setLocationName(booking.getLocation().getParkingName());
        view.setSlotNumber(booking.getSlot().getSlotNumber());
        view.setFloorName(booking.getSlot().getFloor().getFloorName());
        view.setZoneName(booking.getSlot().getZone().getZoneName());
        view.setStart(booking.getStartTime());
        view.setEnd(booking.getEndTime());
        view.setActualEntryTime(booking.getActualEntryTime());

        LocalDateTime now = LocalDateTime.now();
        switch (booking.getBookingStatus()) {
            case CANCELLED -> {
                view.setCheckInBlockReason("Booking is cancelled.");
                view.setCheckOutBlockReason("Booking is cancelled.");
            }
            case EXPIRED -> {
                view.setCheckInBlockReason("Booking expired without payment.");
                view.setCheckOutBlockReason("Booking expired without payment.");
            }
            case NO_SHOW -> {
                view.setCheckInBlockReason("Booking was marked as no-show.");
                view.setCheckOutBlockReason("Booking was marked as no-show.");
            }
            case PENDING_PAYMENT -> {
                view.setCheckInBlockReason("Payment is pending for this booking.");
                view.setCheckOutBlockReason("No active session.");
            }
            case CONFIRMED -> {
                if (booking.getPaymentStatus() != PaymentStatus.CAPTURED) {
                    view.setCheckInBlockReason("Payment is not confirmed.");
                } else if (plate != null && booking.getVehicle() != null
                        && !plate.equals(normalize(booking.getVehicle().getVehicleNumber()))) {
                    view.setCheckInBlockReason("Vehicle %s does not match this booking.".formatted(plate));
                } else if (now.isBefore(booking.getStartTime().minusMinutes(30))) {
                    view.setCheckInBlockReason("Too early — entry opens 30 minutes before start.");
                } else if (now.isAfter(booking.getEndTime())) {
                    view.setCheckInBlockReason("Booking window has passed. Ask for an extension first.");
                } else {
                    view.setCanCheckIn(true);
                }
                view.setCheckOutBlockReason("No active session.");
            }
            case CHECKED_IN -> {
                view.setCheckInBlockReason("Already checked in at %s.".formatted(booking.getActualEntryTime()));
                sessionRepository.findByBookingId(booking.getId())
                        .filter(s -> "ACTIVE".equals(s.getSessionStatus()))
                        .ifPresentOrElse(
                                s -> view.setCanCheckOut(true),
                                () -> view.setCheckOutBlockReason("No active session."));
            }
            case COMPLETED -> {
                view.setCheckInBlockReason("Booking already completed.");
                view.setCheckOutBlockReason("Already checked out.");
            }
        }
        return view;
    }

    private ApiException checkInError(StaffDto.VerifyResponse view) {
        String reason = view.getCheckInBlockReason();
        if (reason != null && reason.contains("Payment")) {
            return ApiException.badRequest(ErrorCode.PAYMENT_REQUIRED, reason);
        }
        if (reason != null && reason.contains("Already checked in")) {
            return ApiException.conflict(ErrorCode.ALREADY_CHECKED_IN, reason);
        }
        if (reason != null && reason.contains("cancelled")) {
            return ApiException.conflict(ErrorCode.BOOKING_CANCELLED, reason);
        }
        return ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                reason == null ? "Check-in is not allowed." : reason);
    }

    private void matchVehicle(ParkingBooking booking, String plate) {
        if (plate != null && booking.getVehicle() != null
                && !plate.equals(normalize(booking.getVehicle().getVehicleNumber()))) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                    "Vehicle %s does not match this booking.".formatted(plate));
        }
    }

    private long overstayCharge(ParkingBooking booking, LocalDateTime exit) {
        if (!exit.isAfter(booking.getEndTime())) {
            return 0L;
        }
        long extraMinutes = Duration.between(booking.getEndTime(), exit).toMinutes();
        long extraHours = Math.max(1, (extraMinutes + 59) / 60);
        VehicleType type = booking.getVehicle() != null
                ? booking.getVehicle().getVehicleType()
                : booking.getSlot().getVehicleType();
        long rate = pricingRepository.findEffective(booking.getLocation().getId(), exit).stream()
                .filter(p -> p.getVehicleType() == type && p.getPricingType() == PricingType.HOURLY)
                .mapToLong(ParkingPricing::getHourlyRatePaise)
                .findFirst()
                .orElse(5000L);
        return extraHours * rate;
    }

    private void writeLog(ParkingSession session, ParkingBooking booking, UUID staffId,
                          String eventType, String remarks) {
        // Load (never fabricate) the staff row: a transient User would break the flush.
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> ApiException.forbidden("Staff account not found."));
        EntryExitLog log = new EntryExitLog();
        log.setSession(session);
        log.setBooking(booking);
        log.setStaff(staff);
        log.setEventType(eventType);
        log.setEventTime(LocalDateTime.now());
        log.setRemarks(remarks);
        logRepository.save(log);
    }

    private String presentedPlate(StaffDto.ScanRequest request) {
        return request.getVehicleNumber() == null || request.getVehicleNumber().isBlank()
                ? null : normalize(request.getVehicleNumber());
    }

    private String normalize(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "");
    }

    private String money(long paise) {
        return "₹" + BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100)).toPlainString();
    }

    private StaffDto.SessionResponse toSessionResponse(ParkingSession session) {
        ParkingBooking booking = session.getBooking();
        StaffDto.SessionResponse response = new StaffDto.SessionResponse();
        response.setSessionId(session.getId());
        response.setBookingReference(booking.getBookingReference());
        response.setSlotNumber(session.getSlot().getSlotNumber());
        response.setEntryTime(session.getEntryTime());
        response.setExitTime(session.getExitTime());
        response.setDurationMinutes(session.getDurationMinutes());
        response.setExtraPaise(session.getExtraAmountPaise());
        response.setSessionStatus(session.getSessionStatus());
        response.setBookingStatus(booking.getBookingStatus().name());
        response.setPaymentStatus(booking.getPaymentStatus().name());
        return response;
    }

    /** Visible for tests. */
    Set<UUID> staffScopeForTest(UUID staffId) {
        return staffScope(staffId);
    }
}
