package com.smartpark.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.booking.BookingDto;
import com.smartpark.booking.BookingService;
import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.booking.PaymentStatus;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.payment.Payment;
import com.smartpark.payment.PaymentRepository;
import com.smartpark.payment.PaymentService;
import com.smartpark.payment.RazorpayGateway;
import com.smartpark.qr.QrPassRepository;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class QrCheckinIT {

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
    private ParkingSessionRepository sessionRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QrPassRepository passRepository;

    @Autowired
    private com.smartpark.vehicle.VehicleRepository vehicleRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final UUID CITY_CENTRE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody())
                .path("data").path("tokens").path("accessToken").asText();
    }

    private UUID rahulId() {
        return userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow().getId();
    }

    private ParkingSlot slot(String number) {
        return slotRepository.findByLocationIdOrderBySlotNumberAsc(CITY_CENTRE).stream()
                .filter(s -> s.getSlotNumber().equals(number)).findFirst().orElseThrow();
    }

    private ResponseEntity<String> call(HttpMethod method, String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        String payload = body == null ? null : objectMapper.writeValueAsString(body);
        return rest.exchange(path, method, new HttpEntity<>(payload, headers), String.class);
    }

    /** Paid + confirmed booking starting in 10 minutes (inside the entry window). */
    private String paidBooking(String slotNumber) {
        UUID userId = rahulId();
        LocalDateTime start = LocalDateTime.now().plusMinutes(10).withSecond(0).withNano(0);
        BookingDto.SlotWindowRequest request = new BookingDto.SlotWindowRequest();
        request.setLocationId(CITY_CENTRE);
        request.setSlotId(slot(slotNumber).getId());
        request.setVehicleType("CAR");
        request.setVehicleId(vehicleRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(v -> v.getVehicleNumber().equals("MH12AB1234"))
                .findFirst().orElseThrow().getId());
        request.setStart(start);
        request.setEnd(start.plusHours(2));
        BookingDto.HoldResponse held = bookingService.hold(userId, request);

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder(
                        "order_%s".formatted(slotNumber), held.getTotalPaise(), "INR", "SP-x"));
        paymentService.createOrder(userId, held.getBookingReference());
        when(gateway.fetchPayment("pay_%s".formatted(slotNumber))).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_%s".formatted(slotNumber),
                        "captured", "upi", held.getTotalPaise()));
        com.smartpark.payment.PaymentDto.VerifyRequest verify =
                new com.smartpark.payment.PaymentDto.VerifyRequest();
        verify.setRazorpayOrderId("order_%s".formatted(slotNumber));
        verify.setRazorpayPaymentId("pay_%s".formatted(slotNumber));
        verify.setRazorpaySignature("sig");
        paymentService.verify(userId, verify);
        return held.getBookingReference();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void qrTicketDisplayedAndPrintable() throws Exception {
        String reference = paidBooking("C020");
        assertThat(passRepository.findByBookingId(
                bookingRepository.findByBookingReference(reference).orElseThrow().getId())).isPresent();

        String customer = login("rahul@dev.local", "Password123!");
        ResponseEntity<String> ticket = call(HttpMethod.GET,
                "/api/v1/bookings/" + reference + "/qr", null, customer);
        assertThat(ticket.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(ticket.getBody()).path("data");
        assertThat(data.path("token").asText()).isNotBlank();
        assertThat(data.path("dataUri").asText()).startsWith("data:image/png;base64,");
        assertThat(data.path("slotNumber").asText()).isEqualTo("C020");

        ResponseEntity<byte[]> png = rest.exchange("/api/v1/bookings/" + reference + "/qr/png",
                HttpMethod.GET,
                new HttpEntity<>(authHeader(customer)), byte[].class);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(png.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        byte[] body = png.getBody();
        assertThat(body).isNotNull();
        // PNG magic bytes.
        assertThat(body[0]).isEqualTo((byte) 0x89);
        assertThat(body[1]).isEqualTo((byte) 'P');
    }

    private HttpHeaders authHeader(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void staffVerifyCheckinCheckout() throws Exception {
        String reference = paidBooking("C021");
        String staff = login("staff1@dev.local", "Password123!");

        ResponseEntity<String> verified = call(HttpMethod.POST, "/api/v1/staff/verify",
                Map.of("bookingReference", reference), staff);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode view = objectMapper.readTree(verified.getBody()).path("data");
        assertThat(view.path("canCheckIn").asBoolean()).isTrue();
        assertThat(view.path("slotNumber").asText()).isEqualTo("C021");

        ResponseEntity<String> in = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("bookingReference", reference, "vehicleNumber", "MH12AB1234"), staff);
        assertThat(in.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode session = objectMapper.readTree(in.getBody()).path("data");
        assertThat(session.path("sessionStatus").asText()).isEqualTo("ACTIVE");
        assertThat(session.path("bookingStatus").asText()).isEqualTo("CHECKED_IN");

        // Double check-in is rejected.
        ResponseEntity<String> again = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("bookingReference", reference), staff);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // On-time checkout: no extra, slot freed.
        ResponseEntity<String> out = call(HttpMethod.POST, "/api/v1/staff/check-out",
                Map.of("bookingReference", reference), staff);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode receipt = objectMapper.readTree(out.getBody()).path("data");
        assertThat(receipt.path("sessionStatus").asText()).isEqualTo("COMPLETED");
        assertThat(receipt.path("extraPaise").asLong()).isEqualTo(0L);
        assertThat(receipt.path("bookingStatus").asText()).isEqualTo("COMPLETED");

        UUID slotId = bookingRepository.findByBookingReference(reference).orElseThrow()
                .getSlot().getId();
        assertThat(slotRepository.findById(slotId).orElseThrow()
                .getPhysicalOccupancyStatus().name()).isEqualTo("FREE");

        // Second checkout is rejected.
        ResponseEntity<String> outAgain = call(HttpMethod.POST, "/api/v1/staff/check-out",
                Map.of("bookingReference", reference), staff);
        assertThat(outAgain.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void staffScopeAndVehicleMismatchEnforced() throws Exception {
        String reference = paidBooking("C022");
        // staff2 is on duty at Phoenix, not City Centre.
        String outsider = login("staff2@dev.local", "Password123!");
        ResponseEntity<String> denied = call(HttpMethod.POST, "/api/v1/staff/verify",
                Map.of("bookingReference", reference), outsider);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String staff = login("staff1@dev.local", "Password123!");
        ResponseEntity<String> mismatch = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("bookingReference", reference, "vehicleNumber", "MH99ZZ9999"), staff);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Checkout without check-in is rejected.
        ResponseEntity<String> early = call(HttpMethod.POST, "/api/v1/staff/check-out",
                Map.of("bookingReference", reference), staff);
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void overstayBilledAndSettledAsTopUp() throws Exception {
        String reference = paidBooking("C023");
        String staff = login("staff1@dev.local", "Password123!");
        ResponseEntity<String> in = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("bookingReference", reference), staff);
        assertThat(in.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Push the whole visit into the past: 3h stay against a 2h booking.
        ParkingBooking booking = bookingRepository.findByBookingReference(reference).orElseThrow();
        jdbcTemplate.update(
                "UPDATE parking_sessions SET entry_time = now() - interval '3 hours' WHERE booking_id = ?",
                booking.getId());
        jdbcTemplate.update(
                "UPDATE parking_bookings SET start_time = now() - interval '3 hours',"
                        + " end_time = now() - interval '1 hour' WHERE id = ?",
                booking.getId());

        ResponseEntity<String> out = call(HttpMethod.POST, "/api/v1/staff/check-out",
                Map.of("bookingReference", reference), staff);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode receipt = objectMapper.readTree(out.getBody()).path("data");
        // 1h overstay × ₹50/hr.
        assertThat(receipt.path("extraPaise").asLong()).isEqualTo(5000L);
        assertThat(receipt.path("paymentStatus").asText()).isEqualTo("PENDING");

        // Customer settles the extra as a top-up order.
        String customer = login("rahul@dev.local", "Password123!");
        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_extra1", 5000L, "INR", "SP-EXTRA-x"));
        ResponseEntity<String> extra = call(HttpMethod.POST, "/api/v1/payments/extra",
                Map.of("bookingReference", reference), customer);
        assertThat(extra.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(extra.getBody()).path("data").path("amountPaise").asLong())
                .isEqualTo(5000L);

        when(gateway.fetchPayment("pay_extra1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_extra1", "captured", "upi", 5000L));
        ResponseEntity<String> settled = call(HttpMethod.POST, "/api/v1/payments/verify",
                Map.of("razorpayOrderId", "order_extra1", "razorpayPaymentId", "pay_extra1",
                        "razorpaySignature", "sig"),
                customer);
        assertThat(settled.getStatusCode()).as(() -> settled.getBody()).isEqualTo(HttpStatus.OK);
        ParkingBooking done = bookingRepository.findByBookingReference(reference).orElseThrow();
        assertThat(done.getPaymentStatus().name()).isEqualTo("CAPTURED");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void cancelRevokesQrPass() throws Exception {
        String reference = paidBooking("C024");
        String customer = login("rahul@dev.local", "Password123!");
        String token = objectMapper.readTree(call(HttpMethod.GET,
                        "/api/v1/bookings/" + reference + "/qr", null, customer).getBody())
                .path("data").path("token").asText();

        bookingService.cancel(rahulId(), reference, "change of plans");

        String staff = login("staff1@dev.local", "Password123!");
        ResponseEntity<String> scan = call(HttpMethod.POST, "/api/v1/staff/verify",
                Map.of("qrToken", token), staff);
        assertThat(scan.getStatusCode().is4xxClientError()).isTrue();

        // The ticket itself is gone too.
        ResponseEntity<String> gone = call(HttpMethod.GET,
                "/api/v1/bookings/" + reference + "/qr", null, customer);
        assertThat(gone.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void qrScanEndToEnd() throws Exception {
        String reference = paidBooking("C025");
        String customer = login("rahul@dev.local", "Password123!");
        String token = objectMapper.readTree(call(HttpMethod.GET,
                        "/api/v1/bookings/" + reference + "/qr", null, customer).getBody())
                .path("data").path("token").asText();

        String staff = login("staff1@dev.local", "Password123!");
        ResponseEntity<String> verified = call(HttpMethod.POST, "/api/v1/staff/verify",
                Map.of("qrToken", token), staff);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> in = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("qrToken", token), staff);
        assertThat(in.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> out = call(HttpMethod.POST, "/api/v1/staff/check-out",
                Map.of("qrToken", token), staff);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Reused QR after completion: nothing left to do.
        ResponseEntity<String> reused = call(HttpMethod.POST, "/api/v1/staff/check-in",
                Map.of("qrToken", token), staff);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
