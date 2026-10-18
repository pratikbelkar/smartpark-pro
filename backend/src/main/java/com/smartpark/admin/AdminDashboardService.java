package com.smartpark.admin;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.booking.PaymentStatus;
import com.smartpark.parking.LocationStatus;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingOwnerRepository;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.parking.SlotOperationalStatus;
import com.smartpark.parking.VerificationStatus;
import com.smartpark.payment.Payment;
import com.smartpark.payment.PaymentRepository;
import com.smartpark.revenue.OwnerLedgerEntryRepository;
import com.smartpark.revenue.OwnerSettlementRepository;
import com.smartpark.session.ParkingSessionRepository;
import com.smartpark.user.Roles;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Platform analytics. Aggregations run in Java over bounded windows (v1
 * scale); revisit with native rollups if booking volume grows.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final ParkingOwnerRepository ownerRepository;
    private final ParkingLocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;
    private final ParkingBookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final ParkingSessionRepository sessionRepository;
    private final OwnerLedgerEntryRepository ledgerRepository;
    private final OwnerSettlementRepository settlementRepository;
    private final com.smartpark.payment.RefundRepository refundRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<Payment> captured = paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED)
                .toList();
        long todayGross = captured.stream()
                .filter(p -> !p.getCreatedAt().isBefore(startOfDay))
                .mapToLong(Payment::getPaymentAmountPaise).sum();
        long monthGross = captured.stream()
                .filter(p -> !p.getCreatedAt().isBefore(startOfMonth))
                .mapToLong(Payment::getPaymentAmountPaise).sum();

        Map<String, Object> cards = new LinkedHashMap<>();
        cards.put("totalCustomers", (long) userRoleRepository.findUsersByRoleName(Roles.CUSTOMER).size());
        cards.put("activeOwners", ownerRepository.findAll().stream()
                .filter(o -> o.getVerificationStatus() == VerificationStatus.APPROVED).count());
        cards.put("totalLocations", locationRepository.findByDeletedAtIsNull(
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
        cards.put("totalSlots", slotRepository.count());
        cards.put("availableSlots", slotRepository.findAll().stream()
                .filter(s -> s.getOperationalStatus() == SlotOperationalStatus.AVAILABLE).count());
        java.util.Set<UUID> reservedToday = new java.util.HashSet<>();
        for (var location : locationRepository.findByDeletedAtIsNull(
                org.springframework.data.domain.Pageable.unpaged()).getContent()) {
            bookingRepository.findBlockingOverlapsForLocation(
                            location.getId(), startOfDay, startOfDay.plusDays(1))
                    .forEach(b -> reservedToday.add(b.getSlot().getId()));
        }
        cards.put("reservedSlots", (long) reservedToday.size());
        cards.put("occupiedSlots", sessionRepository.findAll().stream()
                .filter(s -> "ACTIVE".equals(s.getSessionStatus())).count());
        cards.put("maintenanceSlots", slotRepository.findAll().stream()
                .filter(s -> s.getOperationalStatus() == SlotOperationalStatus.MAINTENANCE).count());
        cards.put("todayBookings", bookingRepository.findByCreatedAtBetween(startOfDay, startOfDay.plusDays(1)).size());
        cards.put("activeSessions", cards.get("occupiedSlots"));
        cards.put("todayGrossPaise", todayGross);
        cards.put("todayPlatformPaise", platformRevenue(captured, startOfDay, LocalDateTime.now().plusDays(1)));
        cards.put("monthPlatformPaise", platformRevenue(captured, startOfMonth, LocalDateTime.now().plusDays(1)));
        cards.put("monthGrossPaise", monthGross);
        cards.put("pendingRefunds", refundRepository.findAll().stream()
                .filter(r -> "PENDING".equals(r.getStatus())).count());
        cards.put("outstandingSettlementsPaise", settlementRepository.findAll().stream()
                .filter(s -> "PENDING".equals(s.getStatus()) || "PROCESSING".equals(s.getStatus()))
                .mapToLong(s -> s.getAmountPaise()).sum());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cards", cards);
        result.put("bookingsDaily", bookingsDaily(14));
        result.put("revenueDaily", revenueDaily(30, captured));
        result.put("statusDistribution", statusDistribution());
        result.put("vehicleSplit", vehicleSplit());
        result.put("topLocations", topLocations(5));
        result.put("peakHours", peakHours());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> revenue() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        List<Payment> captured = paymentRepository.findAll().stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayGrossPaise", sumBetween(captured, startOfDay, now.plusDays(1)));
        result.put("todayPlatformPaise", platformRevenue(captured, startOfDay, now.plusDays(1)));
        result.put("monthGrossPaise", sumBetween(captured, startOfMonth, now.plusDays(1)));
        result.put("monthPlatformPaise", platformRevenue(captured, startOfMonth, now.plusDays(1)));
        result.put("ownerPayablePaise", ledgerRepository.findAll().stream()
                .mapToLong(e -> e.getAmountPaise()).sum());
        return result;
    }

    // --------------------------------------------------------------- internals

    private long platformRevenue(List<Payment> captured, LocalDateTime from, LocalDateTime to) {
        // Convenience fees on captured bookings + commission ledger debits in window.
        long convenience = bookingRepository.findByCreatedAtBetween(from.minusDays(60), to).stream()
                .filter(b -> b.getPaymentStatus() == PaymentStatus.CAPTURED)
                .filter(b -> !b.getCreatedAt().isBefore(from) && b.getCreatedAt().isBefore(to))
                .mapToLong(ParkingBooking::getConvenienceFeePaise)
                .sum();
        return convenience;
    }

    private long sumBetween(List<Payment> captured, LocalDateTime from, LocalDateTime to) {
        return captured.stream()
                .filter(p -> !p.getCreatedAt().isBefore(from) && p.getCreatedAt().isBefore(to))
                .mapToLong(Payment::getPaymentAmountPaise).sum();
    }

    private List<Map<String, Object>> bookingsDaily(int days) {
        Map<LocalDate, Long> counts = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            counts.put(today.minusDays(i), 0L);
        }
        for (ParkingBooking booking : bookingRepository.findByCreatedAtBetween(
                today.minusDays(days - 1).atStartOfDay(), today.plusDays(1).atStartOfDay())) {
            counts.merge(booking.getCreatedAt().toLocalDate(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((date, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("bookings", count);
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> revenueDaily(int days, List<Payment> captured) {
        Map<LocalDate, Long> gross = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            gross.put(today.minusDays(i), 0L);
        }
        for (Payment payment : captured) {
            LocalDate day = payment.getCreatedAt().toLocalDate();
            if (gross.containsKey(day)) {
                gross.merge(day, payment.getPaymentAmountPaise(), Long::sum);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        gross.forEach((date, amount) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date.toString());
            row.put("grossPaise", amount);
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> statusDistribution() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            counts.put(status.name(), 0L);
        }
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        for (ParkingBooking booking : bookingRepository.findByCreatedAtBetween(from, LocalDateTime.now())) {
            counts.merge(booking.getBookingStatus().name(), 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((status, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", status);
            row.put("count", count);
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> vehicleSplit() {
        Map<String, Long> counts = new LinkedHashMap<>(Map.of("CAR", 0L, "TWO_WHEELER", 0L));
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        for (ParkingBooking booking : bookingRepository.findByCreatedAtBetween(from, LocalDateTime.now())) {
            String type = booking.getVehicle() != null
                    ? booking.getVehicle().getVehicleType().name()
                    : String.valueOf(booking.getPricingSnapshot().getOrDefault("vehicleType", "CAR"));
            counts.merge(type, 1L, Long::sum);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        counts.forEach((type, count) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", type);
            row.put("count", count);
            rows.add(row);
        });
        return rows;
    }

    private List<Map<String, Object>> topLocations(int limit) {
        Map<String, long[]> revenue = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        for (Payment payment : paymentRepository.findByCreatedAtBetween(from, LocalDateTime.now())) {
            if (payment.getPaymentStatus() != PaymentStatus.CAPTURED) {
                continue;
            }
            String id = payment.getBooking().getLocation().getId().toString();
            names.putIfAbsent(id, payment.getBooking().getLocation().getParkingName());
            revenue.computeIfAbsent(id, k -> new long[1])[0] += payment.getPaymentAmountPaise();
        }
        return revenue.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("locationId", e.getKey());
                    row.put("locationName", names.get(e.getKey()));
                    row.put("grossPaise", e.getValue()[0]);
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> peakHours() {
        long[] hours = new long[24];
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        for (ParkingBooking booking : bookingRepository.findByCreatedAtBetween(from, LocalDateTime.now())) {
            hours[booking.getStartTime().getHour()]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", h);
            row.put("bookings", hours[h]);
            rows.add(row);
        }
        return rows;
    }

    /** Rounds paise for display safety. */
    static long round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
