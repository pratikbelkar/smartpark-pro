package com.smartpark.payment;

import com.smartpark.audit.AuditService;
import com.smartpark.booking.BookingService;
import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.HoldStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.booking.PaymentStatus;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.notification.EmailService;
import com.smartpark.notification.NotificationService;
import com.smartpark.qr.QrService;
import com.smartpark.revenue.OwnerLedgerEntry;
import com.smartpark.revenue.OwnerLedgerEntryRepository;
import com.smartpark.session.ParkingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay test-mode flow. Trust order: gateway fetch > webhook > frontend
 * callback. A booking is confirmed only after server-side verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ParkingBookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentWebhookEventRepository webhookRepository;
    private final OwnerLedgerEntryRepository ledgerRepository;
    private final BookingService bookingService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final RazorpayGateway gateway;
    private final QrService qrService;
    private final ParkingSessionRepository sessionRepository;

    @org.springframework.beans.factory.annotation.Value("${app.razorpay.key-id:}")
    private String razorpayKeyId;

    // ---------------------------------------------------------------- orders

    /** Creates (or reuses) a Razorpay order for the amount currently due. */
    @Transactional
    public PaymentDto.OrderResponse createOrder(UUID userId, String reference) {
        ParkingBooking booking = ownedBooking(userId, reference);
        if (booking.getBookingStatus() != BookingStatus.PENDING_PAYMENT
                && !(booking.getPaymentStatus() == PaymentStatus.PENDING
                        && (booking.getBookingStatus() == BookingStatus.CONFIRMED
                                || booking.getBookingStatus() == BookingStatus.CHECKED_IN))) {
            throw ApiException.conflict(ErrorCode.PAYMENT_FAILED,
                    "This booking is not awaiting payment.");
        }
        if (booking.getBookingStatus() == BookingStatus.PENDING_PAYMENT
                && (booking.getHold() == null || booking.getHold().getStatus() != HoldStatus.ACTIVE
                        || booking.getHold().getExpiresAt().isBefore(LocalDateTime.now()))) {
            expireLapsed(booking);
            throw ApiException.badRequest(ErrorCode.HOLD_EXPIRED,
                    "The payment window expired and the slot was released. Please book again.");
        }

        long due = amountDue(booking);
        if (due <= 0) {
            throw ApiException.conflict(ErrorCode.PAYMENT_FAILED, "No amount is due on this booking.");
        }

        List<Payment> existing = paymentRepository.findAllByBookingId(booking.getId());
        for (Payment payment : existing) {
            if (payment.getPaymentStatus() == PaymentStatus.PENDING
                    && payment.getPaymentAmountPaise() == due) {
                return orderResponse(booking, payment);
            }
        }

        RazorpayGateway.GatewayOrder order = gateway.createOrder(due, "INR",
                "SP-%s".formatted(booking.getBookingReference()),
                Map.of("bookingReference", booking.getBookingReference()));

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setRazorpayOrderId(order.id());
        payment.setPaymentAmountPaise(due);
        payment.setCurrency(order.currency());
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);
        if (booking.getPaymentStatus() == PaymentStatus.CREATED) {
            booking.setPaymentStatus(PaymentStatus.PENDING);
        }

        auditService.log(userId, "PAYMENT_ORDER_CREATED", "payments", payment.getId().toString(),
                Map.of("orderId", order.id(), "amountPaise", due));
        return orderResponse(booking, payment);
    }

    // ---------------------------------------------------------------- verify

    /**
     * Checkout callback handler. Verifies the HMAC signature, then the
     * authoritative gateway state — never trusts the frontend alone.
     */
    @Transactional
    public PaymentDto.VerifyResponse verify(UUID userId, PaymentDto.VerifyRequest request) {
        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId().trim())
                .orElseThrow(() -> ApiException.notFound("Payment order not found."));
        ParkingBooking booking = payment.getBooking();
        if (!booking.getCustomer().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this payment.");
        }
        if (payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
            return verifyResponse(booking); // idempotent replay
        }

        gateway.verifyPaymentSignature(payment.getRazorpayOrderId(),
                request.getRazorpayPaymentId().trim(), request.getRazorpaySignature().trim());

        RazorpayGateway.AuthoritativePayment authoritative =
                gateway.fetchPayment(request.getRazorpayPaymentId().trim());
        if (authoritative.amountPaise() != payment.getPaymentAmountPaise()) {
            throw ApiException.badRequest(ErrorCode.PAYMENT_FAILED,
                    "Paid amount does not match the order. Contact support.");
        }
        RazorpayGateway.AuthoritativePayment settled = authoritative;
        if ("authorized".equalsIgnoreCase(authoritative.status())) {
            settled = gateway.capturePayment(authoritative.id(), payment.getPaymentAmountPaise());
        }
        if (!"captured".equalsIgnoreCase(settled.status())) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setRazorpayPaymentId(request.getRazorpayPaymentId().trim());
            notificationService.notify(booking.getCustomer(), "PAYMENT_FAILED",
                    "Payment failed for %s".formatted(booking.getBookingReference()),
                    "Gateway status: %s. Your slot hold is still active — please retry."
                            .formatted(settled.status()));
            throw ApiException.badRequest(ErrorCode.PAYMENT_FAILED,
                    "Payment was not captured (status: %s).".formatted(settled.status()));
        }

        settleCapture(payment, booking, settled.id(), settled.method(), Map.of("source", "verify"));
        return verifyResponse(booking);
    }

    // --------------------------------------------------------------- webhooks

    /** Raw-body HMAC check, unique event id, then reconcile. */
    @Transactional
    public Map<String, String> webhook(String payload, String signature) {
        gateway.verifyWebhookSignature(payload, signature);
        JSONObject event;
        try {
            event = new JSONObject(payload);
        } catch (Exception ex) {
            throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "Invalid webhook payload.");
        }
        String eventId = event.optString("id", "");
        String type = event.optString("event", "");
        if (eventId.isBlank()) {
            throw ApiException.badRequest(ErrorCode.BAD_REQUEST, "Webhook event has no id.");
        }
        if (webhookRepository.findByGatewayEventId(eventId).isPresent()) {
            return Map.of("status", "IGNORED"); // replay-safe
        }

        PaymentWebhookEvent record = new PaymentWebhookEvent();
        record.setGatewayEventId(eventId);
        record.setEventType(type.isBlank() ? "unknown" : type);
        webhookRepository.save(record);

        try {
            routeWebhook(record, event);
            record.setProcessingStatus("PROCESSED");
        } catch (ApiException ex) {
            record.setProcessingStatus("FAILED");
            throw ex;
        } finally {
            record.setProcessedAt(LocalDateTime.now());
        }
        return Map.of("status", record.getProcessingStatus());
    }

    private void routeWebhook(PaymentWebhookEvent record, JSONObject event) {
        String type = record.getEventType();
        JSONObject entity = event.optJSONObject("payload") == null ? null
                : event.optJSONObject("payload").optJSONObject("payment") == null ? null
                : event.optJSONObject("payload").optJSONObject("payment").optJSONObject("entity");
        if (entity == null) {
            record.setProcessingStatus("IGNORED");
            return;
        }
        String razorpayPaymentId = entity.optString("id", "");
        String orderId = entity.optString("order_id", "");

        switch (type) {
            case "payment.captured", "order.paid" -> {
                Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
                if (payment == null || payment.getPaymentStatus() == PaymentStatus.CAPTURED) {
                    record.setProcessingStatus(payment == null ? "IGNORED" : "PROCESSED");
                    return;
                }
                RazorpayGateway.AuthoritativePayment authoritative = gateway.fetchPayment(razorpayPaymentId);
                if (!"captured".equalsIgnoreCase(authoritative.status())) {
                    record.setProcessingStatus("IGNORED");
                    return;
                }
                settleCapture(payment, payment.getBooking(), razorpayPaymentId,
                        entity.optString("method", null), Map.of("source", "webhook", "event", record.getGatewayEventId()));
            }
            case "payment.failed" -> {
                Payment payment = paymentRepository.findByRazorpayOrderId(orderId).orElse(null);
                if (payment != null && payment.getPaymentStatus() != PaymentStatus.CAPTURED) {
                    payment.setPaymentStatus(PaymentStatus.FAILED);
                    payment.setRazorpayPaymentId(razorpayPaymentId);
                    notificationService.notify(payment.getBooking().getCustomer(), "PAYMENT_FAILED",
                            "Payment failed for %s".formatted(payment.getBooking().getBookingReference()),
                            "The gateway reported a failure. Your hold stays active until it expires.");
                }
            }
            case "refund.processed", "refund.created" -> {
                String refundId = event.optJSONObject("payload").optJSONObject("refund") == null ? ""
                        : event.optJSONObject("payload").optJSONObject("refund")
                                .optJSONObject("entity").optString("id", "");
                refundRepository.findByRazorpayRefundId(refundId).ifPresent(refund -> {
                    refund.setStatus("PROCESSED");
                    markRefundSettled(refund);
                });
            }
            default -> record.setProcessingStatus("IGNORED");
        }
    }

    // ---------------------------------------------------------------- refunds

    /** Retries gateway settlement for PENDING refunds (scheduler + admin). */
    @Scheduled(fixedDelay = 300_000L)
    @Transactional
    public int processPendingRefunds() {
        List<Refund> pending = refundRepository.findAll().stream()
                .filter(r -> "PENDING".equals(r.getStatus()))
                .toList();
        int done = 0;
        for (Refund refund : pending) {
            try {
                Payment payment = refund.getPayment();
                if (payment.getPaymentStatus() != PaymentStatus.CAPTURED
                        || payment.getRazorpayPaymentId() == null) {
                    continue;
                }
                RazorpayGateway.GatewayRefund gatewayRefund = gateway.refund(
                        payment.getRazorpayPaymentId(), refund.getAmountPaise(),
                        Map.of("bookingReference", refund.getBooking().getBookingReference()));
                refund.setRazorpayRefundId(gatewayRefund.id());
                refund.setStatus("PROCESSED");
                markRefundSettled(refund);
                done++;
            } catch (Exception ex) {
                log.warn("Refund {} failed, will retry.", refund.getId(), ex);
            }
        }
        if (done > 0) {
            log.info("Processed {} refunds.", done);
        }
        return done;
    }

    // ---------------------------------------------------------- overstay top-up

    /**
     * Razorpay order for checkout overstay extras. The gate never blocks on
     * payment: the car leaves, and the customer settles here via verify().
     */
    @Transactional
    public PaymentDto.OrderResponse createExtraOrder(UUID userId, String reference) {
        ParkingBooking booking = ownedBooking(userId, reference);
        long extras = sessionRepository.findByBookingId(booking.getId())
                .map(s -> s.getExtraAmountPaise()).orElse(0L);
        if (extras <= 0) {
            throw ApiException.conflict(ErrorCode.PAYMENT_FAILED, "No extra amount is due on this booking.");
        }
        long captured = paymentRepository.findAllByBookingId(booking.getId()).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED)
                .mapToLong(Payment::getPaymentAmountPaise)
                .sum();
        long due = extras - Math.max(0, captured - booking.getTotalAmountPaise());
        if (due <= 0) {
            throw ApiException.conflict(ErrorCode.PAYMENT_FAILED, "No extra amount is due on this booking.");
        }

        for (Payment payment : paymentRepository.findAllByBookingId(booking.getId())) {
            if (payment.getPaymentStatus() == PaymentStatus.PENDING && payment.getPaymentAmountPaise() == due) {
                return orderResponse(booking, payment);
            }
        }

        RazorpayGateway.GatewayOrder order = gateway.createOrder(due, "INR",
                "SP-EXTRA-%s".formatted(booking.getBookingReference()),
                Map.of("bookingReference", booking.getBookingReference(), "kind", "overstay"));
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setRazorpayOrderId(order.id());
        payment.setPaymentAmountPaise(due);
        payment.setCurrency(order.currency());
        payment.setPaymentStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);
        return orderResponse(booking, payment);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public PaymentDto.PaymentResponse get(UUID userId, Long paymentId, List<String> roles) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        ParkingBooking booking = payment.getBooking();
        boolean owner = roles.contains("PARKING_OWNER")
                && booking.getLocation().getOwner().getUser().getId().equals(userId);
        if (!booking.getCustomer().getId().equals(userId) && !owner && !roles.contains("SUPER_ADMIN")) {
            throw ApiException.forbidden("You do not have access to this payment.");
        }
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto.RefundResponse> listRefunds() {
        return refundRepository.findAll().stream().map(refund -> {
            PaymentDto.RefundResponse response = new PaymentDto.RefundResponse();
            response.setId(refund.getId());
            response.setBookingReference(refund.getBooking().getBookingReference());
            response.setAmountPaise(refund.getAmountPaise());
            response.setStatus(refund.getStatus());
            response.setRazorpayRefundId(refund.getRazorpayRefundId());
            response.setReason(refund.getReason());
            return response;
        }).toList();
    }

    // --------------------------------------------------------------- internals

    private void settleCapture(Payment payment, ParkingBooking booking, String razorpayPaymentId,
                               String method, Map<String, String> context) {
        payment.setPaymentStatus(PaymentStatus.CAPTURED);
        payment.setRazorpayPaymentId(razorpayPaymentId);
        payment.setPaymentMethod(method);
        booking.setPaymentStatus(PaymentStatus.CAPTURED);

        postLedgerEntries(booking, payment.getPaymentAmountPaise());
        bookingService.confirmBooking(booking.getId());
        qrService.issue(booking.getId());

        notificationService.notify(booking.getCustomer(), "PAYMENT_SUCCESS",
                "Payment received for %s".formatted(booking.getBookingReference()),
                "Booking confirmed. Your QR pass is ready in My Bookings.");
        emailService.bookingConfirmed(booking.getCustomer().getEmail(), booking.getBookingReference(),
                booking.getSlot().getSlotNumber(), booking.getLocation().getParkingName(),
                String.valueOf(booking.getStartTime()), String.valueOf(booking.getEndTime()),
                "₹" + BigDecimal.valueOf(payment.getPaymentAmountPaise())
                        .divide(BigDecimal.valueOf(100)).toPlainString());
        auditService.log(booking.getCustomer().getId(), "PAYMENT_CAPTURED", "payments",
                payment.getId().toString(), Map.of("razorpayPaymentId", razorpayPaymentId));
    }

    /**
     * Exact split: the convenience fee is covered first (platform revenue),
     * then parking. Owner gets parking minus commission%; the rest is platform.
     */
    private void postLedgerEntries(ParkingBooking booking, long capturedPaise) {
        List<Payment> captured = paymentRepository.findAllByBookingId(booking.getId()).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED)
                .toList();
        long capturedBefore = captured.stream().mapToLong(Payment::getPaymentAmountPaise).sum() - capturedPaise;
        long feeTotal = booking.getConvenienceFeePaise();
        long feeCoveredBefore = Math.min(Math.max(capturedBefore, 0), feeTotal);
        long feeNow = Math.min(capturedPaise, feeTotal - feeCoveredBefore);
        long parkingNow = capturedPaise - feeNow;
        if (parkingNow <= 0) {
            return;
        }

        BigDecimal commissionPct;
        try {
            Object raw = booking.getPricingSnapshot().getOrDefault("commissionPercent", "15.00");
            commissionPct = new BigDecimal(String.valueOf(raw));
        } catch (Exception ex) {
            commissionPct = new BigDecimal("15.00");
        }
        long commission = BigDecimal.valueOf(parkingNow).multiply(commissionPct)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
        long ownerShare = parkingNow - commission;

        OwnerLedgerEntry credit = new OwnerLedgerEntry();
        credit.setOwner(booking.getLocation().getOwner());
        credit.setBooking(booking);
        credit.setEntryType("BOOKING_CREDIT");
        credit.setAmountPaise(ownerShare);
        ledgerRepository.save(credit);

        if (commission > 0) {
            OwnerLedgerEntry debit = new OwnerLedgerEntry();
            debit.setOwner(booking.getLocation().getOwner());
            debit.setBooking(booking);
            debit.setEntryType("COMMISSION_DEBIT");
            debit.setAmountPaise(-commission);
            ledgerRepository.save(debit);
        }
    }

    private void markRefundSettled(Refund refund) {
        ParkingBooking booking = refund.getBooking();
        Payment payment = refund.getPayment();
        boolean full = refund.getAmountPaise() >= booking.getTotalAmountPaise();
        payment.setPaymentStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        booking.setPaymentStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

        OwnerLedgerEntry debit = new OwnerLedgerEntry();
        debit.setOwner(booking.getLocation().getOwner());
        debit.setBooking(booking);
        debit.setEntryType("REFUND_DEBIT");
        debit.setAmountPaise(-refund.getAmountPaise());
        ledgerRepository.save(debit);

        notificationService.notify(booking.getCustomer(), "REFUND_CONFIRMED",
                "Refund processed for %s".formatted(booking.getBookingReference()),
                "Refund of ₹%s is on its way to the original payment method."
                        .formatted(BigDecimal.valueOf(refund.getAmountPaise())
                                .divide(BigDecimal.valueOf(100)).toPlainString()));
        emailService.refundProcessed(booking.getCustomer().getEmail(), booking.getBookingReference(),
                "₹" + BigDecimal.valueOf(refund.getAmountPaise())
                        .divide(BigDecimal.valueOf(100)).toPlainString());
    }

    /** Lazy expiry on access: a lapsed hold is dead even if the sweep hasn't run yet. */
    private void expireLapsed(ParkingBooking booking) {
        if (booking.getHold() != null && booking.getHold().getStatus() == HoldStatus.ACTIVE
                && booking.getHold().getExpiresAt().isBefore(LocalDateTime.now())) {
            booking.getHold().setStatus(HoldStatus.EXPIRED);
            booking.setBookingStatus(BookingStatus.EXPIRED);
            notificationService.notify(booking.getCustomer(), "BOOKING_EXPIRED",
                    "Booking %s expired".formatted(booking.getBookingReference()),
                    "The payment window lapsed and the slot was released.");
        }
    }

    private long amountDue(ParkingBooking booking) {
        long captured = paymentRepository.findAllByBookingId(booking.getId()).stream()
                .filter(p -> p.getPaymentStatus() == PaymentStatus.CAPTURED)
                .mapToLong(Payment::getPaymentAmountPaise)
                .sum();
        return booking.getTotalAmountPaise() - captured;
    }

    private ParkingBooking ownedBooking(UUID userId, String reference) {
        ParkingBooking booking = bookingRepository.findByBookingReference(reference.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getCustomer().getId().equals(userId)) {
            throw ApiException.forbidden("You do not have access to this booking.");
        }
        return booking;
    }

    private PaymentDto.OrderResponse orderResponse(ParkingBooking booking, Payment payment) {
        return new PaymentDto.OrderResponse(razorpayKeyId, payment.getRazorpayOrderId(),
                payment.getPaymentAmountPaise(), payment.getCurrency(), booking.getBookingReference());
    }

    private PaymentDto.VerifyResponse verifyResponse(ParkingBooking booking) {
        return new PaymentDto.VerifyResponse(booking.getBookingReference(),
                booking.getBookingStatus().name(), booking.getPaymentStatus().name());
    }

    private PaymentDto.PaymentResponse toResponse(Payment payment) {
        PaymentDto.PaymentResponse response = new PaymentDto.PaymentResponse();
        response.setId(payment.getId());
        response.setBookingReference(payment.getBooking().getBookingReference());
        response.setPaymentStatus(payment.getPaymentStatus().name());
        response.setPaymentMethod(payment.getPaymentMethod());
        response.setAmountPaise(payment.getPaymentAmountPaise());
        response.setCurrency(payment.getCurrency());
        response.setRazorpayOrderId(payment.getRazorpayOrderId());
        response.setRazorpayPaymentId(payment.getRazorpayPaymentId());
        return response;
    }
}
