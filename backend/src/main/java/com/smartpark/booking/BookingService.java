package com.smartpark.booking;

import com.smartpark.audit.AuditService;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.notification.EmailService;
import com.smartpark.notification.NotificationService;
import com.smartpark.parking.LocationStatus;
import com.smartpark.parking.ParkingLocation;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingOwner;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.parking.SlotMaintenanceRepository;
import com.smartpark.parking.SlotOperationalStatus;
import com.smartpark.parking.VehicleType;
import com.smartpark.payment.Payment;
import com.smartpark.payment.PaymentRepository;
import com.smartpark.payment.Refund;
import com.smartpark.payment.RefundRepository;
import com.smartpark.qr.QrService;
import com.smartpark.revenue.CancellationPolicy;
import com.smartpark.revenue.CancellationPolicyRepository;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.vehicle.Vehicle;
import com.smartpark.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quote → hold → (Step 5: pay) → confirm lifecycle.
 *
 * Every hold/confirm/extend locks the slot row ({@code FOR UPDATE}) and
 * re-checks holds + confirmed bookings inside one transaction; the
 * exclusion constraints are the final backstop (mapped to 409).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingHoldRepository holdRepository;
    private final ParkingBookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final ParkingLocationRepository locationRepository;
    private final SlotMaintenanceRepository maintenanceRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CancellationPolicyRepository policyRepository;
    private final PricingService pricingService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final QrService qrService;

    @Value("${app.booking.hold-ttl-minutes:10}")
    private int holdTtlMinutes;

    private final SecureRandom random = new SecureRandom();
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    // ----------------------------------------------------------------- quote

    /** Backend-priced preview. No persistence, no locks. */
    @Transactional(readOnly = true)
    public BookingDto.QuoteResponse quote(UUID userId, BookingDto.SlotWindowRequest request) {
        ResolvedTarget target = resolve(request, userId, false);
        PricingService.Quote quote = pricingService.quote(
                target.location().getId(), target.vehicleType(), request.getStart(), request.getEnd());

        BookingDto.QuoteResponse response = new BookingDto.QuoteResponse();
        response.setLocationId(target.location().getId());
        response.setSlotId(target.slot().getId());
        response.setSlotNumber(target.slot().getSlotNumber());
        response.setVehicleType(target.vehicleType().name());
        response.setStart(request.getStart());
        response.setEnd(request.getEnd());
        response.setBillableHours(quote.billableHours());
        response.setParkingPaise(quote.parkingPaise());
        response.setConveniencePaise(quote.conveniencePaise());
        response.setTaxPaise(quote.taxPaise());
        response.setTotalPaise(quote.totalPaise());
        response.setHoldTtlMinutes(holdTtlMinutes);
        response.setPricingSnapshot(quote.snapshot());
        return response;
    }

    // ------------------------------------------------------------------ hold

    /** Locks the slot, blocks inventory with a hold, and opens a PENDING_PAYMENT booking. */
    @Transactional
    public BookingDto.HoldResponse hold(UUID userId, BookingDto.SlotWindowRequest request) {
        User customer = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        ResolvedTarget target = resolve(request, userId, true);
        PricingService.Quote quote = pricingService.quote(
                target.location().getId(), target.vehicleType(), request.getStart(), request.getEnd());

        LocalDateTime now = LocalDateTime.now();
        BookingHold hold = new BookingHold();
        hold.setCustomer(customer);
        hold.setLocation(target.location());
        hold.setSlot(target.slot());
        hold.setVehicle(target.vehicle());
        hold.setStartTime(request.getStart());
        hold.setEndTime(request.getEnd());
        hold.setExpiresAt(now.plusMinutes(holdTtlMinutes));
        hold.setStatus(HoldStatus.ACTIVE);
        hold = holdRepository.save(hold);

        ParkingBooking booking = new ParkingBooking();
        booking.setBookingReference(newReference());
        booking.setCustomer(customer);
        booking.setLocation(target.location());
        booking.setSlot(target.slot());
        booking.setVehicle(target.vehicle());
        booking.setHold(hold);
        booking.setStartTime(request.getStart());
        booking.setEndTime(request.getEnd());
        booking.setParkingAmountPaise(quote.parkingPaise());
        booking.setConvenienceFeePaise(quote.conveniencePaise());
        booking.setApplicableTaxPaise(quote.taxPaise());
        booking.setTotalAmountPaise(quote.totalPaise());
        booking.setPricingSnapshot(quote.snapshot());
        booking.setPaymentStatus(PaymentStatus.CREATED);
        booking.setBookingStatus(BookingStatus.PENDING_PAYMENT);
        booking = bookingRepository.save(booking);

        notificationService.notify(customer, "HOLD_CREATED",
                "Slot %s held for %d minutes".formatted(target.slot().getSlotNumber(), holdTtlMinutes),
                "Complete payment before %s or the hold expires and the slot is released."
                        .formatted(hold.getExpiresAt()));
        auditService.log(userId, "BOOKING_HELD", "parking_bookings", booking.getId().toString(),
                Map.of("reference", booking.getBookingReference()));

        BookingDto.HoldResponse response = new BookingDto.HoldResponse();
        response.setHoldId(hold.getId());
        response.setBookingReference(booking.getBookingReference());
        response.setSlotId(target.slot().getId());
        response.setSlotNumber(target.slot().getSlotNumber());
        response.setStart(request.getStart());
        response.setEnd(request.getEnd());
        response.setExpiresAt(hold.getExpiresAt());
        response.setTotalPaise(quote.totalPaise());
        return response;
    }

    // --------------------------------------------------------------- confirm

    /** Step 5 calls this after verified payment: consume hold, confirm booking. */
    @Transactional
    public ParkingBooking confirmBooking(UUID bookingId) {
        ParkingBooking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT) {
            return booking;
        }
        // Serialize against concurrent confirm/cancel/extend on the same slot.
        slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> ApiException.notFound("Slot not found."));
        if (booking.getHold() != null) {
            booking.getHold().setStatus(HoldStatus.CONSUMED);
        }
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking = bookingRepository.save(booking);

        notificationService.notify(booking.getCustomer(), "BOOKING_CONFIRMED",
                "Booking %s confirmed".formatted(booking.getBookingReference()),
                "Slot %s at %s, %s → %s. Your QR pass is ready."
                        .formatted(booking.getSlot().getSlotNumber(),
                                booking.getLocation().getParkingName(),
                                booking.getStartTime(), booking.getEndTime()));
        auditService.log(booking.getCustomer().getId(), "BOOKING_CONFIRMED",
                "parking_bookings", booking.getId().toString(),
                Map.of("reference", booking.getBookingReference()));
        return booking;
    }

    // ---------------------------------------------------------------- cancel

    @Transactional
    public BookingDto.CancelResponse cancel(UUID userId, String reference, String reason) {
        ParkingBooking booking = ownedBooking(userId, reference);
        return cancelBooking(booking, reason);
    }

    /** Shared customer/staff/admin path once ownership/scope is established. */
    @Transactional
    public BookingDto.CancelResponse cancelBooking(ParkingBooking booking, String reason) {
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            return cancelResponse(booking, 0L, "NONE");
        }
        if (booking.getBookingStatus() == BookingStatus.CHECKED_IN
                || booking.getBookingStatus() == BookingStatus.COMPLETED) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                    "This booking is already in use or completed and cannot be cancelled.");
        }
        if (!LocalDateTime.now().isBefore(booking.getStartTime())) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                    "Cancellation is only possible before the booking start time.");
        }

        long refundPaise = 0L;
        String refundStatus = "NONE";
        Payment payment = paymentRepository.findByBookingId(booking.getId()).orElse(null);
        boolean paid = payment != null && payment.getPaymentStatus() == PaymentStatus.CAPTURED;

        if (paid && booking.getBookingStatus() == BookingStatus.CONFIRMED) {
            refundPaise = refundFor(booking);
            if (refundPaise > 0) {
                Refund refund = new Refund();
                refund.setPayment(payment);
                refund.setBooking(booking);
                refund.setAmountPaise(refundPaise);
                refund.setStatus("PENDING");
                refund.setReason(reason == null ? "Customer cancellation" : reason);
                refundRepository.save(refund);
                booking.setPaymentStatus(PaymentStatus.REFUND_PENDING);
                refundStatus = "PENDING";
            }
        }

        if (booking.getHold() != null && booking.getHold().getStatus() == HoldStatus.ACTIVE) {
            booking.getHold().setStatus(HoldStatus.EXPIRED);
        }
        booking.setBookingStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        qrService.revoke(booking.getId());

        notificationService.notify(booking.getCustomer(), "BOOKING_CANCELLED",
                "Booking %s cancelled".formatted(booking.getBookingReference()),
                refundPaise > 0 ? "Refund of %s is being processed.".formatted(money(refundPaise))
                        : "No amount was due back for this cancellation.");
        emailService.bookingCancelled(booking.getCustomer().getEmail(), booking.getBookingReference(),
                refundPaise > 0 ? "Refund of %s is being processed.".formatted(money(refundPaise))
                        : "No amount was due back for this cancellation.");
        auditService.log(booking.getCustomer().getId(), "BOOKING_CANCELLED",
                "parking_bookings", booking.getId().toString(),
                Map.of("reference", booking.getBookingReference()));
        return cancelResponse(booking, refundPaise, refundStatus);
    }

    // ---------------------------------------------------------------- extend

    @Transactional
    public BookingDto.ExtendResponse extend(UUID userId, String reference, LocalDateTime newEnd) {
        ParkingBooking booking = ownedBooking(userId, reference);
        if (booking.getBookingStatus() != BookingStatus.CONFIRMED
                && booking.getBookingStatus() != BookingStatus.CHECKED_IN) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT,
                    "Only confirmed or active bookings can be extended.");
        }
        if (newEnd == null || !newEnd.isAfter(booking.getEndTime())) {
            throw ApiException.unprocessable("New end time must be after the current end time.");
        }

        ParkingSlot slot = slotRepository.findByIdForUpdate(booking.getSlot().getId())
                .orElseThrow(() -> ApiException.notFound("Slot not found."));
        List<ParkingBooking> conflicts = bookingRepository
                .findBlockingOverlaps(List.of(slot.getId()), booking.getEndTime(), newEnd).stream()
                .filter(b -> !b.getId().equals(booking.getId()))
                .toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE,
                    "Extension conflicts with another reservation. Try a shorter extension.");
        }
        List<BookingHold> holds = holdRepository.findActiveOverlaps(
                List.of(slot.getId()), booking.getEndTime(), newEnd);
        if (!holds.isEmpty()) {
            throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE,
                    "Extension conflicts with another reservation. Try a shorter extension.");
        }

        VehicleType type = booking.getVehicle() != null
                ? booking.getVehicle().getVehicleType()
                : VehicleType.valueOf(String.valueOf(
                        booking.getPricingSnapshot().getOrDefault("vehicleType", "CAR")));
        PricingService.Quote requote = pricingService.quote(
                booking.getLocation().getId(), type, booking.getStartTime(), newEnd);

        long extraDue = Math.max(0, requote.totalPaise() - booking.getTotalAmountPaise());
        booking.setEndTime(newEnd);
        if (extraDue > 0) {
            booking.setParkingAmountPaise(requote.parkingPaise());
            booking.setConvenienceFeePaise(requote.conveniencePaise());
            booking.setApplicableTaxPaise(requote.taxPaise());
            booking.setTotalAmountPaise(requote.totalPaise());
            booking.setPricingSnapshot(requote.snapshot());
            booking.setPaymentStatus(PaymentStatus.PENDING);
        }
        bookingRepository.save(booking);

        notificationService.notify(booking.getCustomer(), "EXTENSION_CONFIRMED",
                "Booking extended to %s".formatted(newEnd),
                extraDue > 0 ? "Additional %s is due.".formatted(money(extraDue))
                        : "No extra charge for this extension.");
        BookingDto.ExtendResponse response = new BookingDto.ExtendResponse();
        response.setBookingReference(booking.getBookingReference());
        response.setNewEnd(newEnd);
        response.setExtraDuePaise(extraDue);
        response.setTotalPaise(booking.getTotalAmountPaise());
        response.setPaymentStatus(booking.getPaymentStatus().name());
        return response;
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public BookingDto.BookingResponse getMine(UUID userId, String reference) {
        return toResponse(ownedBooking(userId, reference));
    }

    @Transactional(readOnly = true)
    public Page<BookingDto.BookingResponse> myBookings(UUID userId, Pageable pageable) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<BookingDto.BookingResponse> ownerBookings(ParkingOwner owner, UUID locationId,
                                                          Pageable pageable) {
        Page<ParkingBooking> page = locationId == null
                ? bookingRepository.findByLocationOwnerIdOrderByCreatedAtDesc(owner.getId(), pageable)
                : bookingRepository.findByLocationOwnerIdAndLocationIdOrderByCreatedAtDesc(
                        owner.getId(), locationId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingDto.BookingResponse ownerBooking(ParkingOwner owner, String reference) {
        ParkingBooking booking = bookingRepository.findByBookingReference(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getLocation().getOwner().getId().equals(owner.getId())) {
            throw ApiException.forbidden("This booking belongs to another owner.");
        }
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingDto.BookingResponse> adminBookings(String status, Pageable pageable) {
        if (status == null || status.isBlank()) {
            return bookingRepository.findAll(pageable).map(this::toResponse);
        }
        try {
            BookingStatus parsed = BookingStatus.valueOf(status.trim().toUpperCase());
            return bookingRepository.findByBookingStatusOrderByCreatedAtDesc(parsed, pageable)
                    .map(this::toResponse);
        } catch (IllegalArgumentException ex) {
            throw ApiException.unprocessable("Unknown booking status.");
        }
    }

    // --------------------------------------------------------------- helpers

    private ParkingBooking ownedBooking(UUID userId, String reference) {
        ParkingBooking booking = bookingRepository.findByBookingReference(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getCustomer().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this booking.");
        }
        return booking;
    }

    private record ResolvedTarget(ParkingLocation location, ParkingSlot slot,
                                  VehicleType vehicleType, Vehicle vehicle) {
    }

    private ResolvedTarget resolve(BookingDto.SlotWindowRequest request, UUID userId, boolean lock) {
        LocalDateTime start = request.getStart();
        LocalDateTime end = request.getEnd();
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.unprocessable("End time must be after start time.");
        }
        if (start.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw ApiException.unprocessable("Start time cannot be in the past.");
        }
        if (Duration.between(start, end).toHours() > 24) {
            throw ApiException.unprocessable("A single booking can span at most 24 hours.");
        }

        ParkingLocation location = parkingLocation(request.getLocationId());
        Vehicle vehicle = null;
        VehicleType type;
        if (request.getVehicleId() != null) {
            vehicle = vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
            type = vehicle.getVehicleType();
        } else {
            try {
                type = VehicleType.valueOf(request.getVehicleType().trim().toUpperCase());
            } catch (Exception ex) {
                throw ApiException.unprocessable("Vehicle type must be TWO_WHEELER or CAR.");
            }
        }

        ParkingSlot slot;
        if (request.getSlotId() != null) {
            slot = lock
                    ? slotRepository.findByIdForUpdate(request.getSlotId())
                            .orElseThrow(() -> ApiException.notFound("Slot not found."))
                    : slotRepository.findById(request.getSlotId())
                            .orElseThrow(() -> ApiException.notFound("Slot not found."));
            if (!slot.getLocation().getId().equals(location.getId())) {
                throw ApiException.unprocessable("Slot does not belong to this parking location.");
            }
            if (slot.getVehicleType() != type) {
                throw ApiException.unprocessable("Slot %s is for %s, not %s."
                        .formatted(slot.getSlotNumber(), slot.getVehicleType(), type));
            }
            assertSlotFree(slot, start, end);
        } else {
            slot = autoAllocate(location.getId(), type, start, end, lock);
        }
        return new ResolvedTarget(location, slot, type, vehicle);
    }

    private ParkingLocation parkingLocation(UUID locationId) {
        ParkingLocation location = locationRepository.findByIdAndDeletedAtIsNull(locationId)
                .orElseThrow(() -> ApiException.notFound("Parking location not found."));
        if (location.getOperationalStatus() != LocationStatus.OPEN) {
            throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE, "This parking location is currently closed.");
        }
        return location;
    }

    private void assertSlotFree(ParkingSlot slot, LocalDateTime start, LocalDateTime end) {
        if (slot.getOperationalStatus() != SlotOperationalStatus.AVAILABLE) {
            throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE,
                    "Slot %s is not available.".formatted(slot.getSlotNumber()));
        }
        List<UUID> ids = List.of(slot.getId());
        if (!maintenanceRepository.findOverlapping(ids, start, end).isEmpty()
                || !holdRepository.findActiveOverlaps(ids, start, end).isEmpty()
                || !bookingRepository.findBlockingOverlaps(ids, start, end).isEmpty()) {
            throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE,
                    "Slot %s is already reserved for the selected time.".formatted(slot.getSlotNumber()));
        }
    }

    private ParkingSlot autoAllocate(UUID locationId, VehicleType type,
                                     LocalDateTime start, LocalDateTime end, boolean lock) {
        List<ParkingSlot> candidates = slotRepository
                .findByLocationIdAndVehicleTypeAndOperationalStatusOrderBySlotNumberAsc(
                        locationId, type, SlotOperationalStatus.AVAILABLE);
        for (ParkingSlot candidate : candidates) {
            ParkingSlot slot = lock
                    ? slotRepository.findByIdForUpdate(candidate.getId()).orElseThrow()
                    : candidate;
            List<UUID> ids = List.of(slot.getId());
            if (maintenanceRepository.findOverlapping(ids, start, end).isEmpty()
                    && holdRepository.findActiveOverlaps(ids, start, end).isEmpty()
                    && bookingRepository.findBlockingOverlaps(ids, start, end).isEmpty()) {
                return slot;
            }
        }
        throw ApiException.conflict(ErrorCode.SLOT_UNAVAILABLE,
                "No %s slots free for the selected time.".formatted(type == VehicleType.CAR ? "car" : "two-wheeler"));
    }

    private long refundFor(ParkingBooking booking) {
        long hoursUntilStart = Duration.between(LocalDateTime.now(), booking.getStartTime()).toHours();
        List<CancellationPolicy> tiers = policyRepository
                .findByLocationIdOrderByHoursBeforeStartDesc(booking.getLocation().getId());
        if (tiers.isEmpty()) {
            tiers = policyRepository.findByLocationIsNullOrderByHoursBeforeStartDesc();
        }
        BigDecimal percent = BigDecimal.ZERO;
        for (CancellationPolicy tier : tiers) {
            if (hoursUntilStart >= tier.getHoursBeforeStart()) {
                percent = tier.getRefundPercent();
                break;
            }
        }
        if (percent.signum() <= 0) {
            return 0L;
        }
        long base = booking.getParkingAmountPaise() + booking.getApplicableTaxPaise();
        return BigDecimal.valueOf(base).multiply(percent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }

    private String newReference() {
        for (int i = 0; i < 5; i++) {
            StringBuilder ref = new StringBuilder("SP");
            for (int j = 0; j < 8; j++) {
                ref.append(REF_ALPHABET.charAt(random.nextInt(REF_ALPHABET.length())));
            }
            String candidate = ref.toString();
            if (bookingRepository.findByBookingReference(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not mint a booking reference.");
    }

    private BookingDto.CancelResponse cancelResponse(ParkingBooking booking, long refundPaise, String refundStatus) {
        BookingDto.CancelResponse response = new BookingDto.CancelResponse();
        response.setBookingReference(booking.getBookingReference());
        response.setBookingStatus(booking.getBookingStatus().name());
        response.setRefundPaise(refundPaise);
        response.setRefundStatus(refundStatus);
        return response;
    }

    private String money(long paise) {
        return "₹" + BigDecimal.valueOf(paise).divide(BigDecimal.valueOf(100)).toPlainString();
    }

    public BookingDto.BookingResponse toResponse(ParkingBooking booking) {
        BookingDto.BookingResponse response = new BookingDto.BookingResponse();
        response.setId(booking.getId());
        response.setBookingReference(booking.getBookingReference());
        response.setBookingStatus(booking.getBookingStatus().name());
        response.setPaymentStatus(booking.getPaymentStatus().name());
        response.setLocationId(booking.getLocation().getId());
        response.setLocationName(booking.getLocation().getParkingName());
        response.setSlotNumber(booking.getSlot().getSlotNumber());
        response.setFloorName(booking.getSlot().getFloor().getFloorName());
        response.setZoneName(booking.getSlot().getZone().getZoneName());
        response.setVehicleNumber(booking.getVehicle() == null ? null : booking.getVehicle().getVehicleNumber());
        response.setStart(booking.getStartTime());
        response.setEnd(booking.getEndTime());
        response.setActualEntryTime(booking.getActualEntryTime());
        response.setActualExitTime(booking.getActualExitTime());
        response.setParkingPaise(booking.getParkingAmountPaise());
        response.setConveniencePaise(booking.getConvenienceFeePaise());
        response.setTaxPaise(booking.getApplicableTaxPaise());
        response.setTotalPaise(booking.getTotalAmountPaise());
        response.setCreatedAt(booking.getCreatedAt());
        return response;
    }
}
