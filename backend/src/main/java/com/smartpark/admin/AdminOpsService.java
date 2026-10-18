package com.smartpark.admin;

import com.smartpark.audit.AuditLogRepository;
import com.smartpark.audit.AuditService;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.exception.ApiException;
import com.smartpark.notification.NotificationService;
import com.smartpark.parking.ParkingOwner;
import com.smartpark.parking.ParkingOwnerRepository;
import com.smartpark.payment.Payment;
import com.smartpark.payment.PaymentRepository;
import com.smartpark.revenue.OwnerLedgerEntry;
import com.smartpark.revenue.OwnerSettlement;
import com.smartpark.revenue.OwnerSettlementRepository;
import com.smartpark.revenue.PlatformFee;
import com.smartpark.revenue.PlatformFeeRepository;
import com.smartpark.review.ReviewRepository;
import com.smartpark.subscription.OwnerSubscription;
import com.smartpark.subscription.OwnerSubscriptionRepository;
import com.smartpark.subscription.SubscriptionPlan;
import com.smartpark.subscription.SubscriptionPlanRepository;
import com.smartpark.support.SupportTicket;
import com.smartpark.support.SupportTicketRepository;
import com.smartpark.user.AccountStatus;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Back-office operations: users, payments, reviews, audit, fees, settlements, support. */
@Service
@RequiredArgsConstructor
public class AdminOpsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PaymentRepository paymentRepository;
    private final ParkingBookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final AuditLogRepository auditLogRepository;
    private final PlatformFeeRepository feeRepository;
    private final OwnerSettlementRepository settlementRepository;
    private final ParkingOwnerRepository ownerRepository;
    private final com.smartpark.revenue.OwnerLedgerEntryRepository ledgerRepository;
    private final SupportTicketRepository ticketRepository;
    private final SubscriptionPlanRepository planRepository;
    private final OwnerSubscriptionRepository subscriptionRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;

    // ----------------------------------------------------------------- users

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<Map<String, Object>> users(String search, String role, Pageable pageable) {
        Page<User> page = search == null || search.isBlank()
                ? userRepository.findAll(pageable)
                : userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        search.trim(), search.trim(), pageable);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User user : page.getContent()) {
            List<String> roles = userRoleRepository.findRoleNamesByUserId(user.getId());
            if (role != null && !role.isBlank()
                    && !roles.contains(role.trim().toUpperCase(Locale.ROOT))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("fullName", user.getFullName());
            row.put("email", user.getEmail());
            row.put("phone", user.getPhone());
            row.put("accountStatus", user.getAccountStatus().name());
            row.put("roles", roles);
            row.put("createdAt", user.getCreatedAt());
            rows.add(row);
        }
        return new org.springframework.data.domain.PageImpl<>(rows, pageable, page.getTotalElements());
    }

    @Transactional
    public Map<String, Object> updateUserStatus(UUID userId, String statusRaw, UUID adminId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found."));
        AccountStatus status;
        try {
            status = AccountStatus.valueOf(statusRaw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw ApiException.unprocessable("Status must be ACTIVE, SUSPENDED or DELETED.");
        }
        user.setAccountStatus(status);
        auditService.log(adminId, "USER_STATUS_" + status.name(), "users", user.getId().toString(), Map.of());
        return Map.of("id", user.getId(), "accountStatus", status.name());
    }

    // -------------------------------------------------------------- payments

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> payments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(payment -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", payment.getId());
            row.put("bookingReference", payment.getBooking().getBookingReference());
            row.put("customerEmail", payment.getBooking().getCustomer().getEmail());
            row.put("amountPaise", payment.getPaymentAmountPaise());
            row.put("paymentStatus", payment.getPaymentStatus().name());
            row.put("paymentMethod", payment.getPaymentMethod());
            row.put("razorpayOrderId", payment.getRazorpayOrderId());
            row.put("razorpayPaymentId", payment.getRazorpayPaymentId());
            row.put("createdAt", payment.getCreatedAt());
            return row;
        });
    }

    // --------------------------------------------------------------- reviews

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> reviews(Pageable pageable) {
        return reviewRepository.findAll(pageable).map(review -> {
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

    // ----------------------------------------------------------------- audit

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> auditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getId());
            row.put("actorUserId", entry.getActorUserId());
            row.put("action", entry.getAction());
            row.put("entityType", entry.getEntityType());
            row.put("entityId", entry.getEntityId());
            row.put("metadata", entry.getMetadata());
            row.put("createdAt", entry.getCreatedAt());
            return row;
        });
    }

    // ------------------------------------------------------------------ fees

    @Transactional(readOnly = true)
    public Map<String, Object> fees() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> result = new LinkedHashMap<>();
        feeRepository.findEffective("convenience_fee_paise", now)
                .ifPresent(f -> result.put("convenienceFeePaise", f.getValuePaise()));
        feeRepository.findEffective("commission_percent", now)
                .ifPresent(f -> result.put("commissionPercent", f.getValuePercent()));
        return result;
    }

    /** Versioned fee change: closes current rows, opens new ones. History kept. */
    @Transactional
    public Map<String, Object> updateFees(Long conveniencePaise, BigDecimal commissionPercent, UUID adminId) {
        LocalDateTime now = LocalDateTime.now();
        if (conveniencePaise != null) {
            if (conveniencePaise < 0) {
                throw ApiException.unprocessable("Convenience fee cannot be negative.");
            }
            feeRepository.findEffective("convenience_fee_paise", now)
                    .ifPresent(f -> f.setEffectiveUntil(now));
            PlatformFee fee = new PlatformFee();
            fee.setFeeKey("convenience_fee_paise");
            fee.setValuePaise(conveniencePaise);
            fee.setEffectiveFrom(now);
            feeRepository.save(fee);
        }
        if (commissionPercent != null) {
            if (commissionPercent.signum() < 0 || commissionPercent.compareTo(new BigDecimal("100")) > 0) {
                throw ApiException.unprocessable("Commission must be between 0 and 100%.");
            }
            feeRepository.findEffective("commission_percent", now)
                    .ifPresent(f -> f.setEffectiveUntil(now));
            PlatformFee fee = new PlatformFee();
            fee.setFeeKey("commission_percent");
            fee.setValuePercent(commissionPercent);
            fee.setEffectiveFrom(now);
            feeRepository.save(fee);
        }
        auditService.log(adminId, "FEES_UPDATED", "platform_fees", "current", Map.of());
        return fees();
    }

    // ----------------------------------------------------------- settlements

    @Transactional(readOnly = true)
    public List<Map<String, Object>> settlements(UUID ownerId) {
        List<OwnerSettlement> rows = ownerId == null ? settlementRepository.findAll()
                : settlementRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (OwnerSettlement settlement : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", settlement.getId());
            row.put("ownerId", settlement.getOwner().getId());
            row.put("businessName", settlement.getOwner().getBusinessName());
            row.put("settlementReference", settlement.getSettlementReference());
            row.put("amountPaise", settlement.getAmountPaise());
            row.put("status", settlement.getStatus());
            row.put("settledAt", settlement.getSettledAt());
            row.put("createdAt", settlement.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> createSettlement(UUID ownerId, long amountPaise, UUID adminId) {
        ParkingOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Owner not found."));
        if (amountPaise <= 0) {
            throw ApiException.unprocessable("Settlement amount must be positive.");
        }
        long balance = ledgerRepository.balanceOf(ownerId);
        if (amountPaise > balance) {
            throw ApiException.unprocessable("Amount exceeds the owner's payable balance of %d paise.".formatted(balance));
        }
        OwnerSettlement settlement = new OwnerSettlement();
        settlement.setOwner(owner);
        settlement.setSettlementReference("STL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        settlement.setAmountPaise(amountPaise);
        settlement.setStatus("PENDING");
        settlement = settlementRepository.save(settlement);

        OwnerLedgerEntry debit = new OwnerLedgerEntry();
        debit.setOwner(owner);
        debit.setEntryType("SETTLEMENT_DEBIT");
        debit.setAmountPaise(-amountPaise);
        ledgerRepository.save(debit);

        auditService.log(adminId, "SETTLEMENT_CREATED", "owner_settlements",
                settlement.getId().toString(), Map.of("amountPaise", amountPaise));
        return Map.of("id", settlement.getId(),
                "settlementReference", settlement.getSettlementReference(), "status", "PENDING");
    }

    @Transactional
    public Map<String, Object> settleSettlement(Long settlementId, UUID adminId) {
        OwnerSettlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> ApiException.notFound("Settlement not found."));
        if (!"PENDING".equals(settlement.getStatus()) && !"PROCESSING".equals(settlement.getStatus())) {
            throw ApiException.conflict(com.smartpark.exception.ErrorCode.CONFLICT,
                    "Only pending settlements can be marked settled.");
        }
        settlement.setStatus("SETTLED");
        settlement.setSettledAt(LocalDateTime.now());
        notificationService.notify(settlement.getOwner().getUser(), "SETTLEMENT_DONE",
                "Settlement %s completed".formatted(settlement.getSettlementReference()),
                "Record the bank transfer in your books. Contact finance with questions.");
        auditService.log(adminId, "SETTLEMENT_SETTLED", "owner_settlements",
                settlement.getId().toString(), Map.of());
        return Map.of("id", settlement.getId(), "status", "SETTLED");
    }

    // --------------------------------------------------------------- support

    @Transactional(readOnly = true)
    public Page<Map<String, Object>> tickets(String status, Pageable pageable) {
        var page = status == null || status.isBlank()
                ? ticketRepository.findAll(pageable)
                : ticketRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase(Locale.ROOT), pageable);
        return page.map(ticket -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", ticket.getId());
            row.put("subject", ticket.getSubject());
            row.put("body", ticket.getBody());
            row.put("status", ticket.getStatus());
            row.put("requesterEmail", ticket.getRequester() == null ? null : ticket.getRequester().getEmail());
            row.put("createdAt", ticket.getCreatedAt());
            return row;
        });
    }

    @Transactional
    public Map<String, Object> updateTicket(Long ticketId, String statusRaw, UUID adminId) {
        var ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> ApiException.notFound("Ticket not found."));
        String status = statusRaw.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED").contains(status)) {
            throw ApiException.unprocessable("Invalid ticket status.");
        }
        ticket.setStatus(status);
        auditService.log(adminId, "TICKET_" + status, "support_tickets", ticket.getId().toString(), Map.of());
        return Map.of("id", ticket.getId(), "status", status);
    }

    // ---------------------------------------------------------- subscriptions

    @Transactional(readOnly = true)
    public List<Map<String, Object>> plans() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var plan : planRepository.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", plan.getId());
            row.put("planCode", plan.getPlanCode());
            row.put("displayName", plan.getDisplayName());
            row.put("maxLocations", plan.getMaxLocations());
            row.put("capabilities", plan.getCapabilities());
            result.add(row);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> assignPlan(UUID ownerId, String planCode, UUID adminId) {
        ParkingOwner owner = ownerRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Owner not found."));
        SubscriptionPlan plan = planRepository.findByPlanCode(planCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Plan not found."));
        subscriptionRepository.findByOwnerIdOrderByStartsAtDesc(ownerId).stream()
                .filter(s -> s.getEndsAt() == null)
                .forEach(s -> s.setEndsAt(LocalDateTime.now()));
        OwnerSubscription subscription = new OwnerSubscription();
        subscription.setOwner(owner);
        subscription.setPlan(plan);
        subscription.setStartsAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
        auditService.log(adminId, "PLAN_ASSIGNED", "owner_subscriptions",
                subscription.getId().toString(), Map.of("plan", plan.getPlanCode()));
        return Map.of("ownerId", ownerId, "planCode", plan.getPlanCode());
    }

    // --------------------------------------------------------------- reports

    @Transactional(readOnly = true)
    public String bookingsCsv(LocalDateTime from, LocalDateTime to, String status) {
        LocalDateTime start = from == null ? LocalDateTime.now().minusDays(30) : from;
        LocalDateTime end = to == null ? LocalDateTime.now().plusDays(1) : to;
        List<ParkingBooking> bookings = bookingRepository.findByCreatedAtBetween(start, end);
        StringBuilder csv = new StringBuilder(
                "booking_reference,status,payment_status,location,owner,slot,vehicle,start,end,total_paise,created_at\n");
        for (ParkingBooking booking : bookings) {
            if (status != null && !status.isBlank()
                    && !booking.getBookingStatus().name().equalsIgnoreCase(status.trim())) {
                continue;
            }
            csv.append(String.join(",",
                    booking.getBookingReference(),
                    booking.getBookingStatus().name(),
                    booking.getPaymentStatus().name(),
                    cell(booking.getLocation().getParkingName()),
                    cell(booking.getLocation().getOwner().getBusinessName()),
                    booking.getSlot().getSlotNumber(),
                    booking.getVehicle() == null ? "" : booking.getVehicle().getVehicleNumber(),
                    booking.getStartTime().toString(),
                    booking.getEndTime().toString(),
                    String.valueOf(booking.getTotalAmountPaise()),
                    booking.getCreatedAt().toString())).append('\n');
        }
        return csv.toString();
    }

    private String cell(String raw) {
        if (raw == null) {
            return "";
        }
        String cell = raw.replace("\"", "\"\"");
        return cell.contains(",") || cell.contains("\"") ? "\"" + cell + "\"" : cell;
    }
}
