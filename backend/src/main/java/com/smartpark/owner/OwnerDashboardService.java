package com.smartpark.owner;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.common.ApiResponse;
import com.smartpark.parking.OwnerService;
import com.smartpark.parking.ParkingLocation;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingOwner;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.parking.SlotOperationalStatus;
import com.smartpark.revenue.OwnerLedgerEntry;
import com.smartpark.revenue.OwnerLedgerEntryRepository;
import com.smartpark.review.ReviewRepository;
import com.smartpark.session.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner home: stats, earnings, CSV exports, profile. All tenant-scoped. */
@Service
@RequiredArgsConstructor
public class OwnerDashboardService {

    private final OwnerService ownerService;
    private final ParkingLocationRepository locationRepository;
    private final ParkingSlotRepository slotRepository;
    private final ParkingBookingRepository bookingRepository;
    private final ParkingSessionRepository sessionRepository;
    private final OwnerLedgerEntryRepository ledgerRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard(UUID userId) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        List<ParkingLocation> locations = locationRepository.findByOwnerIdAndDeletedAtIsNull(owner.getId());
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        long slotsTotal = 0;
        long slotsAvailable = 0;
        for (ParkingLocation location : locations) {
            var slots = slotRepository.findByLocationIdOrderBySlotNumberAsc(location.getId());
            slotsTotal += slots.size();
            slotsAvailable += slots.stream()
                    .filter(s -> s.getOperationalStatus() == SlotOperationalStatus.AVAILABLE).count();
        }

        List<ParkingBooking> today = bookingRepository.findByOwnerAndCreatedBetween(
                owner.getId(), startOfDay, startOfDay.plusDays(1));
        long upcoming = bookingRepository.findByOwnerFiltered(owner.getId(), null,
                        LocalDateTime.now(), LocalDateTime.now().plusDays(30)).stream()
                .filter(b -> b.getBookingStatus() == BookingStatus.CONFIRMED).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("locations", locations.size());
        stats.put("slotsTotal", slotsTotal);
        stats.put("slotsAvailable", slotsAvailable);
        stats.put("bookingsToday", today.size());
        stats.put("activeSessions", sessionRepository.findActiveByOwner(owner.getId()).size());
        stats.put("upcomingBookings", upcoming);
        stats.put("balancePaise", ledgerRepository.balanceOf(owner.getId()));
        stats.put("revenue30dPaise", ledgerRepository.sumSince(owner.getId(), LocalDateTime.now().minusDays(30)));
        return stats;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> earnings(UUID userId) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        List<OwnerLedgerEntry> entries = ledgerRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId());

        Map<UUID, Map<String, Object>> byLocation = new LinkedHashMap<>();
        for (OwnerLedgerEntry entry : entries) {
            UUID locationId = entry.getBooking() == null ? null : entry.getBooking().getLocation().getId();
            String name = entry.getBooking() == null ? "Adjustments"
                    : entry.getBooking().getLocation().getParkingName();
            Map<String, Object> row = byLocation.computeIfAbsent(locationId, k -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("locationId", locationId);
                r.put("locationName", name);
                r.put("grossPaise", 0L);
                r.put("commissionPaise", 0L);
                r.put("refundsPaise", 0L);
                r.put("netPaise", 0L);
                return r;
            });
            switch (entry.getEntryType()) {
                case "BOOKING_CREDIT" -> {
                    row.put("grossPaise", (long) row.get("grossPaise") + entry.getAmountPaise());
                    row.put("netPaise", (long) row.get("netPaise") + entry.getAmountPaise());
                }
                case "COMMISSION_DEBIT" -> {
                    row.put("commissionPaise", (long) row.get("commissionPaise") - entry.getAmountPaise());
                    row.put("netPaise", (long) row.get("netPaise") + entry.getAmountPaise());
                }
                case "REFUND_DEBIT" -> {
                    row.put("refundsPaise", (long) row.get("refundsPaise") - entry.getAmountPaise());
                    row.put("netPaise", (long) row.get("netPaise") + entry.getAmountPaise());
                }
                default -> row.put("netPaise", (long) row.get("netPaise") + entry.getAmountPaise());
            }
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (OwnerLedgerEntry entry : entries.stream().limit(50).toList()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getId());
            row.put("entryType", entry.getEntryType());
            row.put("amountPaise", entry.getAmountPaise());
            row.put("bookingReference",
                    entry.getBooking() == null ? null : entry.getBooking().getBookingReference());
            row.put("createdAt", entry.getCreatedAt());
            recent.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("balancePaise", ledgerRepository.balanceOf(owner.getId()));
        result.put("byLocation", new ArrayList<>(byLocation.values()));
        result.put("recentEntries", recent);
        return result;
    }

    @Transactional(readOnly = true)
    public String bookingsCsv(UUID userId, UUID locationId, LocalDateTime from, LocalDateTime to) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(30) : to;
        List<ParkingBooking> bookings = bookingRepository.findByOwnerFiltered(owner.getId(), locationId, start, end);

        StringBuilder csv = new StringBuilder(
                "booking_reference,status,payment_status,location,slot,vehicle,start,end,parking_paise,fee_paise,tax_paise,total_paise\n");
        for (ParkingBooking booking : bookings) {
            csv.append(String.join(",",
                    booking.getBookingReference(),
                    booking.getBookingStatus().name(),
                    booking.getPaymentStatus().name(),
                    csvCell(booking.getLocation().getParkingName()),
                    booking.getSlot().getSlotNumber(),
                    booking.getVehicle() == null ? "" : booking.getVehicle().getVehicleNumber(),
                    booking.getStartTime().toString(),
                    booking.getEndTime().toString(),
                    String.valueOf(booking.getParkingAmountPaise()),
                    String.valueOf(booking.getConvenienceFeePaise()),
                    String.valueOf(booking.getApplicableTaxPaise()),
                    String.valueOf(booking.getTotalAmountPaise()))).append('\n');
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> reviews(UUID userId, Pageable pageable) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        return reviewRepository.findByLocationOwnerIdOrderByCreatedAtDesc(owner.getId(), pageable)
                .map(review -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", review.getId());
                    row.put("rating", review.getRating());
                    row.put("comment", review.getComment());
                    row.put("locationName", review.getLocation().getParkingName());
                    row.put("customerName", review.getCustomer().getFullName());
                    row.put("createdAt", review.getCreatedAt());
                    return row;
                });
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, Map<String, String> fields) {
        ParkingOwner owner = ownerService.requireApprovedOwner(userId);
        if (fields.containsKey("businessName") && !fields.get("businessName").isBlank()) {
            owner.setBusinessName(fields.get("businessName").trim());
        }
        if (fields.containsKey("businessEmail")) {
            owner.setBusinessEmail(fields.get("businessEmail"));
        }
        if (fields.containsKey("businessPhone")) {
            owner.setBusinessPhone(fields.get("businessPhone"));
        }
        if (fields.containsKey("businessAddress")) {
            owner.setBusinessAddress(fields.get("businessAddress"));
        }
        return ApiResponse.map("id", owner.getId(), "businessName", owner.getBusinessName(),
                "verificationStatus", owner.getVerificationStatus().name());
    }

    private String csvCell(String raw) {
        if (raw == null) {
            return "";
        }
        String cell = raw.replace("\"", "\"\"");
        return cell.contains(",") || cell.contains("\"") ? "\"" + cell + "\"" : cell;
    }
}
