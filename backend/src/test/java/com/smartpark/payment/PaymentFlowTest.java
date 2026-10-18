package com.smartpark.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.booking.BookingDto;
import com.smartpark.booking.BookingService;
import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.revenue.OwnerLedgerEntryRepository;
import com.smartpark.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.razorpay.webhook-secret=whsec_test",
                "app.razorpay.key-id=rzp_test_x",
                "app.razorpay.key-secret=shhh"})
@Testcontainers
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class PaymentFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RazorpayGateway gateway;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ParkingBookingRepository bookingRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ParkingLocationRepository locationRepository;

    @Autowired
    private OwnerLedgerEntryRepository ledgerRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID CITY_CENTRE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private UUID rahulId() {
        return userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow().getId();
    }

    private ParkingSlot slot(String number) {
        return slotRepository.findByLocationIdOrderBySlotNumberAsc(CITY_CENTRE).stream()
                .filter(s -> s.getSlotNumber().equals(number)).findFirst().orElseThrow();
    }

    private BookingDto.SlotWindowRequest window(String slotNumber, LocalDateTime start, LocalDateTime end) {
        BookingDto.SlotWindowRequest request = new BookingDto.SlotWindowRequest();
        request.setLocationId(CITY_CENTRE);
        request.setSlotId(slot(slotNumber).getId());
        request.setVehicleType("CAR");
        request.setStart(start);
        request.setEnd(end);
        return request;
    }

    private LocalDateTime evening(int daysAhead) {
        return LocalDateTime.now().plusDays(daysAhead).withHour(17).withMinute(0).withSecond(0).withNano(0);
    }

    private long ledgerBalance() {
        return ledgerRepository.balanceOf(OWNER1);
    }

    private String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private ResponseEntity<String> webhook(String payload, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Razorpay-Signature", signature);
        return rest.exchange("/api/v1/webhooks/razorpay", HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void gatewaySignaturesAreReallyChecked() {
        // Pure-crypto path on the real implementation (no network involved).
        RazorpayGatewayImpl real = new RazorpayGatewayImpl("rzp_test_x", "shhh", "whsec_test");
        String payload = "{\"id\":\"evt_1\"}";
        String good;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("whsec_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            good = hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        real.verifyWebhookSignature(payload, good);
        try {
            real.verifyWebhookSignature(payload, "deadbeef");
            assertThat(false).as("expected forged webhook signature to fail").isTrue();
        } catch (com.smartpark.exception.ApiException ex) {
            assertThat(ex.getCode()).isEqualTo(com.smartpark.exception.ErrorCode.INVALID_SIGNATURE);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void orderVerifyConfirmAndIdempotency() {
        UUID userId = rahulId();
        long before = ledgerBalance();
        LocalDateTime start = evening(1);
        BookingDto.HoldResponse held = bookingService.hold(userId, window("C010", start, start.plusHours(3)));

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_1", 16000L, "INR", "SP-x"));
        PaymentDto.OrderResponse order = paymentService.createOrder(userId, held.getBookingReference());
        assertThat(order.getOrderId()).isEqualTo("order_1");
        assertThat(order.getAmountPaise()).isEqualTo(16000L);

        // Same amount due -> existing order reused, no duplicate gateway order.
        PaymentDto.OrderResponse again = paymentService.createOrder(userId, held.getBookingReference());
        assertThat(again.getOrderId()).isEqualTo("order_1");

        when(gateway.fetchPayment("pay_1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_1", "captured", "upi", 16000L));
        PaymentDto.VerifyRequest verify = new PaymentDto.VerifyRequest();
        verify.setRazorpayOrderId("order_1");
        verify.setRazorpayPaymentId("pay_1");
        verify.setRazorpaySignature("sig");
        PaymentDto.VerifyResponse verified = paymentService.verify(userId, verify);
        assertThat(verified.getBookingStatus()).isEqualTo("CONFIRMED");

        // Ledger delta: parking 15000 − 15% commission → owner 12750 − 2250 = 10500 net.
        assertThat(ledgerBalance() - before).isEqualTo(10500L);

        // Idempotent replay: no duplicate ledger rows.
        PaymentDto.VerifyResponse replayed = paymentService.verify(userId, verify);
        assertThat(replayed.getBookingStatus()).isEqualTo("CONFIRMED");
        assertThat(ledgerBalance() - before).isEqualTo(10500L);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void webhookCapturesAndIgnoresReplays() throws Exception {
        UUID userId = rahulId();
        LocalDateTime start = evening(1);
        BookingDto.HoldResponse held = bookingService.hold(userId, window("C011", start, start.plusHours(2)));

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_w1", 11000L, "INR", "SP-x"));
        paymentService.createOrder(userId, held.getBookingReference());
        when(gateway.fetchPayment("pay_w1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_w1", "captured", "card", 11000L));

        String payload = """
                {"id":"evt_w1","event":"payment.captured","payload":{"payment":{"entity":\
                {"id":"pay_w1","order_id":"order_w1","status":"captured","method":"card","amount":11000}}}}""";
        ResponseEntity<String> first = webhook(payload, hmac(payload));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(first.getBody()).path("data").path("status").asText())
                .isEqualTo("PROCESSED");
        assertThat(bookingRepository.findByBookingReference(held.getBookingReference()).orElseThrow()
                .getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Replay of the same event id is a no-op.
        ResponseEntity<String> replay = webhook(payload, hmac(payload));
        assertThat(objectMapper.readTree(replay.getBody()).path("data").path("status").asText())
                .isEqualTo("IGNORED");

        // Forged signature is rejected (mock made to enforce it, as the real impl does).
        org.mockito.Mockito.doThrow(com.smartpark.exception.ApiException.unauthorized(
                        com.smartpark.exception.ErrorCode.INVALID_SIGNATURE, "Invalid webhook signature."))
                .when(gateway).verifyWebhookSignature(anyString(),
                        org.mockito.ArgumentMatchers.eq("deadbeef"));
        ResponseEntity<String> forged = webhook(payload, "deadbeef");
        assertThat(forged.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void expiredHoldCannotOrder() {
        UUID userId = rahulId();
        LocalDateTime start = evening(1);
        BookingDto.HoldResponse held = bookingService.hold(userId, window("C012", start, start.plusHours(2)));
        jdbcTemplate.update(
                "UPDATE parking_booking_holds SET created_at = now() - interval '20 minutes',"
                        + " expires_at = now() - interval '10 minutes' WHERE id = ?",
                held.getHoldId());
        try {
            paymentService.createOrder(userId, held.getBookingReference());
            assertThat(false).as("expected expired hold to block ordering").isTrue();
        } catch (com.smartpark.exception.ApiException ex) {
            assertThat(ex.getCode()).isEqualTo(com.smartpark.exception.ErrorCode.HOLD_EXPIRED);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void cancelCreatesRefundAndSchedulerSettlesIt() {
        UUID userId = rahulId();
        LocalDateTime start = evening(3); // >24h ahead -> 100% of parking
        BookingDto.HoldResponse held = bookingService.hold(userId, window("C013", start, start.plusHours(3)));

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_r1", 16000L, "INR", "SP-x"));
        paymentService.createOrder(userId, held.getBookingReference());
        when(gateway.fetchPayment("pay_r1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_r1", "captured", "upi", 16000L));
        PaymentDto.VerifyRequest verify = new PaymentDto.VerifyRequest();
        verify.setRazorpayOrderId("order_r1");
        verify.setRazorpayPaymentId("pay_r1");
        verify.setRazorpaySignature("sig");
        paymentService.verify(userId, verify);

        BookingDto.CancelResponse cancelled = bookingService.cancel(userId, held.getBookingReference(), "plans");
        assertThat(cancelled.getRefundPaise()).isEqualTo(15000L);

        when(gateway.refund(anyString(), anyLong(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayRefund("rfnd_1"));
        int processed = paymentService.processPendingRefunds();
        assertThat(processed).isEqualTo(1);

        com.smartpark.payment.Payment payment = paymentRepository.findByRazorpayOrderId("order_r1").orElseThrow();
        // 15000 of 16000 refunded (fee is non-refundable) -> partial.
        assertThat(payment.getPaymentStatus().name()).isEqualTo("PARTIALLY_REFUNDED");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void customerPaymentApiRoundTrip() throws Exception {
        UUID userId = rahulId();
        LocalDateTime start = evening(1);
        BookingDto.HoldResponse held = bookingService.hold(userId, window("C014", start, start.plusHours(2)));

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_a1", 11000L, "INR", "SP-x"));

        ResponseEntity<String> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "rahul@dev.local", "password", "Password123!"), String.class);
        String token = objectMapper.readTree(login.getBody())
                .path("data").path("tokens").path("accessToken").asText();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        ResponseEntity<String> order = rest.exchange("/api/v1/payments/orders", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(
                        Map.of("bookingReference", held.getBookingReference())), headers),
                String.class);
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode orderData = objectMapper.readTree(order.getBody()).path("data");
        assertThat(orderData.path("orderId").asText()).isEqualTo("order_a1");
        assertThat(orderData.path("keyId").asText()).isEqualTo("rzp_test_x");

        when(gateway.fetchPayment("pay_a1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_a1", "captured", "netbanking", 11000L));
        ResponseEntity<String> verified = rest.exchange("/api/v1/payments/verify", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                        "razorpayOrderId", "order_a1",
                        "razorpayPaymentId", "pay_a1",
                        "razorpaySignature", "sig")), headers),
                String.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(verified.getBody()).path("data").path("bookingStatus").asText())
                .isEqualTo("CONFIRMED");

        // Re-verifying an already-captured payment is a harmless no-op.
        ResponseEntity<String> tampered = rest.exchange("/api/v1/payments/verify", HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(Map.of(
                        "razorpayOrderId", "order_a1",
                        "razorpayPaymentId", "pay_a1",
                        "razorpaySignature", "sig")), headers),
                String.class);
        assertThat(tampered.getStatusCode()).isEqualTo(HttpStatus.OK); // already captured: idempotent
    }
}
