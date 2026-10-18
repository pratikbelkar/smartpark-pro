package com.smartpark.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.booking.BookingDto;
import com.smartpark.booking.BookingService;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.payment.PaymentDto;
import com.smartpark.payment.PaymentService;
import com.smartpark.payment.RazorpayGateway;
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
class AdminConsoleIT {

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
    private ParkingSlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    private static final UUID CITY_CENTRE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RIVERSIDE = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID OWNER1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OWNER2 = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody())
                .path("data").path("tokens").path("accessToken").asText();
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

    private JsonNode data(ResponseEntity<String> response, HttpStatus expected) throws Exception {
        assertThat(response.getStatusCode()).as(response.getBody()).isEqualTo(expected);
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(root.path("ok").asBoolean()).isTrue();
        return root.path("data");
    }

    private ParkingSlot slot(String number) {
        return slotRepository.findByLocationIdOrderBySlotNumberAsc(CITY_CENTRE).stream()
                .filter(s -> s.getSlotNumber().equals(number)).findFirst().orElseThrow();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void ownerDashboardAndExport() throws Exception {
        String owner = login("owner1@dev.local", "Password123!");
        JsonNode dashboard = data(call(HttpMethod.GET, "/api/v1/owner/dashboard", null, owner), HttpStatus.OK);
        assertThat(dashboard.path("locations").asInt()).isEqualTo(2);
        assertThat(dashboard.path("slotsTotal").asLong()).isEqualTo(85L);
        assertThat(dashboard.path("slotsAvailable").asLong()).isEqualTo(85L);

        UUID rahul = userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow().getId();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(17).withMinute(0).withSecond(0).withNano(0);
        BookingDto.SlotWindowRequest request = new BookingDto.SlotWindowRequest();
        request.setLocationId(CITY_CENTRE);
        request.setSlotId(slot("C030").getId());
        request.setVehicleType("CAR");
        request.setStart(start);
        request.setEnd(start.plusHours(2));
        BookingDto.HoldResponse held = bookingService.hold(rahul, request);

        ResponseEntity<String> csv = call(HttpMethod.GET, "/api/v1/owner/bookings/export", null, owner);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).contains("booking_reference").contains(held.getBookingReference());

        JsonNode updated = data(call(HttpMethod.PUT, "/api/v1/owner/me",
                Map.of("businessPhone", "020-99999999"), owner), HttpStatus.OK);
        assertThat(updated.path("businessName").asText()).isEqualTo("Sharma Parking Co");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void staffRosterScopedToOwner() throws Exception {
        String owner = login("owner1@dev.local", "Password123!");
        JsonNode granted = data(call(HttpMethod.POST, "/api/v1/owner/staff",
                Map.of("email", "priya@dev.local"), owner), HttpStatus.OK);
        assertThat(granted.path("email").asText()).isEqualTo("priya@dev.local");

        UUID priya = userRepository.findByEmailIgnoreCase("priya@dev.local").orElseThrow().getId();
        LocalDateTime shiftStart = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        JsonNode shift = data(call(HttpMethod.POST, "/api/v1/owner/staff/shifts",
                Map.of("staffUserId", priya.toString(), "locationId", CITY_CENTRE.toString(),
                        "startsAt", shiftStart.toString(), "endsAt", shiftStart.plusHours(8).toString()),
                owner), HttpStatus.OK);
        assertThat(shift.path("locationName").asText()).isEqualTo("City Centre Parking");

        // Another owner's location is off limits.
        ResponseEntity<String> denied = call(HttpMethod.POST, "/api/v1/owner/staff/shifts",
                Map.of("staffUserId", priya.toString(), "locationId", RIVERSIDE.toString(),
                        "startsAt", shiftStart.toString(), "endsAt", shiftStart.plusHours(8).toString()),
                owner);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode shifts = data(call(HttpMethod.GET, "/api/v1/owner/staff/shifts", null, owner), HttpStatus.OK);
        assertThat(shifts.size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void adminDashboardUsersAndSupport() throws Exception {
        String admin = login("admin@smartpark.local", "ChangeMe123!");
        JsonNode dashboard = data(call(HttpMethod.GET, "/api/v1/admin/dashboard", null, admin), HttpStatus.OK);
        assertThat(dashboard.path("cards").path("totalSlots").asLong()).isEqualTo(100L);
        assertThat(dashboard.path("bookingsDaily").size()).isEqualTo(14);
        assertThat(dashboard.path("peakHours").size()).isEqualTo(24);

        JsonNode users = data(call(HttpMethod.GET, "/api/v1/admin/users?search=rahul", null, admin), HttpStatus.OK);
        assertThat(users.path("totalElements").asInt()).isEqualTo(1);

        // Suspend blocks login; reactivate restores it.
        UUID priya = userRepository.findByEmailIgnoreCase("priya@dev.local").orElseThrow().getId();
        data(call(HttpMethod.PUT, "/api/v1/admin/users/" + priya + "/status",
                Map.of("status", "SUSPENDED"), admin), HttpStatus.OK);
        ResponseEntity<String> blocked = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "priya@dev.local", "password", "Password123!"), String.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        data(call(HttpMethod.PUT, "/api/v1/admin/users/" + priya + "/status",
                Map.of("status", "ACTIVE"), admin), HttpStatus.OK);

        // Fees are versioned, never rewritten.
        JsonNode fees = data(call(HttpMethod.GET, "/api/v1/admin/settings/fees", null, admin), HttpStatus.OK);
        assertThat(fees.path("convenienceFeePaise").asLong()).isEqualTo(1000L);
        JsonNode updated = data(call(HttpMethod.PUT, "/api/v1/admin/settings/fees",
                Map.of("conveniencePaise", 1500), admin), HttpStatus.OK);
        assertThat(updated.path("convenienceFeePaise").asLong()).isEqualTo(1500L);

        // Support round-trip.
        String rahul = login("rahul@dev.local", "Password123!");
        JsonNode ticket = data(call(HttpMethod.POST, "/api/v1/support",
                Map.of("subject", "Gate was slow", "body", "Took 10 minutes at entry."), rahul), HttpStatus.OK);
        assertThat(ticket.path("status").asText()).isEqualTo("OPEN");
        JsonNode tickets = data(call(HttpMethod.GET, "/api/v1/admin/support", null, admin), HttpStatus.OK);
        assertThat(tickets.path("totalElements").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void settlementFlowAndPlans() throws Exception {
        UUID rahul = userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow().getId();
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        BookingDto.SlotWindowRequest request = new BookingDto.SlotWindowRequest();
        request.setLocationId(CITY_CENTRE);
        request.setSlotId(slot("C029").getId());
        request.setVehicleType("CAR");
        request.setStart(start);
        request.setEnd(start.plusHours(2));
        BookingDto.HoldResponse held = bookingService.hold(rahul, request);

        when(gateway.createOrder(anyLong(), anyString(), anyString(), anyMap()))
                .thenReturn(new RazorpayGateway.GatewayOrder("order_s1", held.getTotalPaise(), "INR", "SP-x"));
        paymentService.createOrder(rahul, held.getBookingReference());
        when(gateway.fetchPayment("pay_s1")).thenReturn(
                new RazorpayGateway.AuthoritativePayment("pay_s1", "captured", "upi", held.getTotalPaise()));
        PaymentDto.VerifyRequest verify = new PaymentDto.VerifyRequest();
        verify.setRazorpayOrderId("order_s1");
        verify.setRazorpayPaymentId("pay_s1");
        verify.setRazorpaySignature("sig");
        paymentService.verify(rahul, verify);

        String admin = login("admin@smartpark.local", "ChangeMe123!");
        String owner = login("owner1@dev.local", "Password123!");
        JsonNode earnings = data(call(HttpMethod.GET, "/api/v1/owner/earnings", null, owner), HttpStatus.OK);
        long balance = earnings.path("balancePaise").asLong();
        assertThat(balance).isGreaterThan(0);

        // Over-balance settlement is refused.
        ResponseEntity<String> tooMuch = call(HttpMethod.POST, "/api/v1/admin/settlements",
                Map.of("ownerId", OWNER2.toString(), "amountPaise", balance + 1), admin);
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode created = data(call(HttpMethod.POST, "/api/v1/admin/settlements",
                Map.of("ownerId", OWNER1.toString(), "amountPaise", balance), admin), HttpStatus.OK);
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        JsonNode settled = data(call(HttpMethod.POST,
                "/api/v1/admin/settlements/" + created.path("id").asLong() + "/settle", null, admin),
                HttpStatus.OK);
        assertThat(settled.path("status").asText()).isEqualTo("SETTLED");

        JsonNode plans = data(call(HttpMethod.GET, "/api/v1/admin/subscription-plans", null, admin),
                HttpStatus.OK);
        assertThat(plans.size()).isEqualTo(4);
        JsonNode assigned = data(call(HttpMethod.POST, "/api/v1/admin/owners/" + OWNER2 + "/subscription",
                Map.of("planCode", "ENTERPRISE"), admin), HttpStatus.OK);
        assertThat(assigned.path("planCode").asText()).isEqualTo("ENTERPRISE");
    }
}
