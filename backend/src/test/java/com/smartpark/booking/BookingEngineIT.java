package com.smartpark.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.parking.ParkingLocation;
import com.smartpark.parking.ParkingLocationRepository;
import com.smartpark.parking.ParkingSlot;
import com.smartpark.parking.ParkingSlotRepository;
import com.smartpark.parking.SlotOperationalStatus;
import com.smartpark.parking.VehicleType;
import com.smartpark.payment.Payment;
import com.smartpark.payment.PaymentRepository;
import com.smartpark.user.User;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class BookingEngineIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingLifecycleService lifecycleService;

    @Autowired
    private BookingHoldRepository holdRepository;

    @Autowired
    private ParkingBookingRepository bookingRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private ParkingLocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private static final UUID CITY_CENTRE = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private String rahulToken() throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "rahul@dev.local", "password", "Password123!"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody())
                .path("data").path("tokens").path("accessToken").asText();
    }

    private UUID rahulId() {
        return userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow().getId();
    }

    private ParkingSlot slot(String number) {
        return slotRepository.findByLocationIdOrderBySlotNumberAsc(CITY_CENTRE).stream()
                .filter(s -> s.getSlotNumber().equals(number))
                .findFirst().orElseThrow();
    }

    private BookingDto.SlotWindowRequest window(UUID locationId, UUID slotId, LocalDateTime start,
                                               LocalDateTime end) {
        BookingDto.SlotWindowRequest request = new BookingDto.SlotWindowRequest();
        request.setLocationId(locationId);
        request.setSlotId(slotId);
        request.setVehicleType("CAR");
        request.setStart(start);
        request.setEnd(end);
        return request;
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

    private LocalDateTime tomorrowAt(int hour) {
        return LocalDateTime.now().plusDays(1).withHour(hour).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void quotePricesOnBackend() throws Exception {
        String token = rahulToken();
        ParkingSlot c001 = slot("C001");
        Map<String, Object> body = Map.of(
                "locationId", CITY_CENTRE.toString(),
                "slotId", c001.getId().toString(),
                "vehicleType", "CAR",
                "start", tomorrowAt(17).toString(),
                "end", tomorrowAt(20).toString());
        ResponseEntity<String> response = call(HttpMethod.POST, "/api/v1/bookings/quote", body, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = objectMapper.readTree(response.getBody()).path("data");
        // 3h at ₹50/hr (15 min grace keeps it at 3 billable hours) + ₹10 fee.
        assertThat(data.path("parkingPaise").asLong()).isEqualTo(15000L);
        assertThat(data.path("conveniencePaise").asLong()).isEqualTo(1000L);
        assertThat(data.path("totalPaise").asLong()).isEqualTo(16000L);
        assertThat(data.path("slotNumber").asText()).isEqualTo("C001");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void holdBlocksDoubleBooking() throws Exception {
        UUID userId = rahulId();
        ParkingSlot c002 = slot("C002");
        BookingDto.SlotWindowRequest request = window(CITY_CENTRE, c002.getId(), tomorrowAt(17), tomorrowAt(20));

        BookingDto.HoldResponse held = bookingService.hold(userId, request);
        assertThat(held.getSlotNumber()).isEqualTo("C002");

        ParkingBooking booking = bookingRepository.findByBookingReference(held.getBookingReference()).orElseThrow();
        assertThat(booking.getBookingStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.getTotalAmountPaise()).isEqualTo(16000L);

        // Same slot + overlapping window -> 409, even though booking is unpaid (hold blocks).
        try {
            bookingService.hold(userId, window(CITY_CENTRE, c002.getId(), tomorrowAt(18), tomorrowAt(21)));
            assertThat(false).as("expected double booking to fail").isTrue();
        } catch (Exception ex) {
            assertThat(ex).isInstanceOfAny(com.smartpark.exception.ApiException.class,
                    org.springframework.dao.DataIntegrityViolationException.class,
                    jakarta.persistence.PersistenceException.class,
                    org.springframework.orm.jpa.JpaSystemException.class);
        }

        // Adjacent window on the same slot succeeds.
        BookingDto.HoldResponse adjacent = bookingService.hold(
                userId, window(CITY_CENTRE, c002.getId(), tomorrowAt(20), tomorrowAt(22)));
        assertThat(adjacent.getBookingReference()).isNotBlank();
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void concurrentHoldsForSameSlotYieldOneWinner() throws Exception {
        UUID userId = rahulId();
        ParkingSlot c003 = slot("C003");
        BookingDto.SlotWindowRequest request = window(CITY_CENTRE, c003.getId(), tomorrowAt(17), tomorrowAt(20));

        int threads = 4;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch gun = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    gun.await();
                    bookingService.hold(userId, request);
                    wins.incrementAndGet();
                } catch (Exception ex) {
                    blocked.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        gun.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(wins.get()).isEqualTo(1);
        assertThat(blocked.get()).isEqualTo(threads - 1);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    void lifecycleSweepsExpireAndNoShow() {
        UUID userId = rahulId();
        ParkingSlot c004 = slot("C004");

        // Hold that already lapsed -> sweep expires hold + unpaid booking.
        BookingDto.HoldResponse held = bookingService.hold(
                userId, window(CITY_CENTRE, c004.getId(), tomorrowAt(17), tomorrowAt(20)));
        // Simulate time passing at the SQL level (entity path guards expiry).
        jdbcTemplate.update(
                "UPDATE parking_booking_holds SET created_at = now() - interval '20 minutes',"
                        + " expires_at = now() - interval '10 minutes' WHERE id = ?",
                held.getHoldId());

        lifecycleService.runOnce();

        assertThat(holdRepository.findById(held.getHoldId()).orElseThrow().getStatus())
                .isEqualTo(HoldStatus.EXPIRED);
        assertThat(bookingRepository.findByBookingReference(held.getBookingReference()).orElseThrow()
                .getBookingStatus()).isEqualTo(BookingStatus.EXPIRED);

        // Confirmed booking past its grace with no entry -> NO_SHOW.
        ParkingBooking booking = new ParkingBooking();
        ParkingLocation location = locationRepository.findById(CITY_CENTRE).orElseThrow();
        User customer = userRepository.findById(userId).orElseThrow();
        booking.setBookingReference("SPTEST01");
        booking.setCustomer(customer);
        booking.setLocation(location);
        booking.setSlot(c004);
        booking.setStartTime(LocalDateTime.now().minusHours(2));
        booking.setEndTime(LocalDateTime.now().minusHours(1));
        booking.setParkingAmountPaise(5000);
        booking.setConvenienceFeePaise(1000);
        booking.setApplicableTaxPaise(0);
        booking.setTotalAmountPaise(6000);
        booking.setPaymentStatus(com.smartpark.booking.PaymentStatus.CAPTURED);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        lifecycleService.runOnce();

        assertThat(bookingRepository.findByBookingReference("SPTEST01").orElseThrow().getBookingStatus())
                .isEqualTo(BookingStatus.NO_SHOW);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    void cancelRefundsByPolicyTier() throws Exception {
        UUID userId = rahulId();
        ParkingSlot c005 = slot("C005");

        // >24h ahead -> 100% of parking back.
        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(17).withMinute(0).withSecond(0).withNano(0);
        BookingDto.HoldResponse held = bookingService.hold(
                userId, window(CITY_CENTRE, c005.getId(), start, start.plusHours(3)));
        ParkingBooking booking = bookingRepository.findByBookingReference(held.getBookingReference()).orElseThrow();

        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setRazorpayOrderId("order_test_%s".formatted(UUID.randomUUID().toString().substring(0, 8)));
        payment.setPaymentAmountPaise(booking.getTotalAmountPaise());
        payment.setPaymentStatus(com.smartpark.booking.PaymentStatus.CAPTURED);
        paymentRepository.save(payment);
        booking.setPaymentStatus(com.smartpark.booking.PaymentStatus.CAPTURED);
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        BookingDto.CancelResponse cancelled = bookingService.cancel(userId, held.getBookingReference(), "change");
        assertThat(cancelled.getBookingStatus()).isEqualTo("CANCELLED");
        assertThat(cancelled.getRefundPaise()).isEqualTo(15000L);
        assertThat(cancelled.getRefundStatus()).isEqualTo("PENDING");

        // <4h ahead -> no refund.
        ParkingSlot c006 = slot("C006");
        LocalDateTime soon = LocalDateTime.now().plusHours(2);
        BookingDto.HoldResponse late = bookingService.hold(
                userId, window(CITY_CENTRE, c006.getId(), soon, soon.plusHours(2)));
        ParkingBooking lateBooking = bookingRepository.findByBookingReference(late.getBookingReference()).orElseThrow();
        Payment latePayment = new Payment();
        latePayment.setBooking(lateBooking);
        latePayment.setRazorpayOrderId("order_test_%s".formatted(UUID.randomUUID().toString().substring(0, 8)));
        latePayment.setPaymentAmountPaise(lateBooking.getTotalAmountPaise());
        latePayment.setPaymentStatus(com.smartpark.booking.PaymentStatus.CAPTURED);
        paymentRepository.save(latePayment);
        lateBooking.setPaymentStatus(com.smartpark.booking.PaymentStatus.CAPTURED);
        lateBooking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(lateBooking);

        BookingDto.CancelResponse lateCancel = bookingService.cancel(userId, late.getBookingReference(), null);
        assertThat(lateCancel.getRefundPaise()).isEqualTo(0L);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    void extendRepricesAndDetectsConflicts() {
        UUID userId = rahulId();
        ParkingSlot c007 = slot("C007");
        LocalDateTime start = tomorrowAt(17);
        BookingDto.HoldResponse held = bookingService.hold(
                userId, window(CITY_CENTRE, c007.getId(), start, start.plusHours(2)));
        ParkingBooking booking = bookingRepository.findByBookingReference(held.getBookingReference()).orElseThrow();
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // +1h: 2h→3h at ₹50/hr = +₹50 due.
        BookingDto.ExtendResponse extended = bookingService.extend(
                userId, held.getBookingReference(), start.plusHours(3));
        assertThat(extended.getExtraDuePaise()).isEqualTo(5000L);
        assertThat(extended.getPaymentStatus()).isEqualTo("PENDING");

        // Block the next hour with another customer's hold -> further extend conflicts.
        User priya = userRepository.findByEmailIgnoreCase("priya@dev.local").orElseThrow();
        BookingHold blocker = new BookingHold();
        blocker.setCustomer(priya);
        blocker.setLocation(locationRepository.findById(CITY_CENTRE).orElseThrow());
        blocker.setSlot(c007);
        blocker.setStartTime(start.plusHours(3));
        blocker.setEndTime(start.plusHours(4));
        blocker.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        blocker.setStatus(HoldStatus.ACTIVE);
        holdRepository.save(blocker);

        try {
            bookingService.extend(userId, held.getBookingReference(), start.plusHours(4));
            assertThat(false).as("expected conflicting extension to fail").isTrue();
        } catch (com.smartpark.exception.ApiException ex) {
            assertThat(ex.getCode())
                    .isEqualTo(com.smartpark.exception.ErrorCode.SLOT_UNAVAILABLE);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    void customerApiRoundTrip() throws Exception {
        String token = rahulToken();
        ParkingSlot c008 = slot("C008");
        Map<String, Object> holdBody = Map.of(
                "locationId", CITY_CENTRE.toString(),
                "slotId", c008.getId().toString(),
                "vehicleType", "CAR",
                "start", tomorrowAt(17).toString(),
                "end", tomorrowAt(19).toString());
        ResponseEntity<String> held = call(HttpMethod.POST, "/api/v1/bookings/hold", holdBody, token);
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        String reference = objectMapper.readTree(held.getBody()).path("data").path("bookingReference").asText();

        ResponseEntity<String> mine = call(HttpMethod.GET, "/api/v1/bookings/my", null, token);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> one = call(HttpMethod.GET, "/api/v1/bookings/" + reference, null, token);
        assertThat(one.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Another customer cannot read it.
        ResponseEntity<String> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", "priya@dev.local", "password", "Password123!"), String.class);
        String priyaToken = objectMapper.readTree(login.getBody())
                .path("data").path("tokens").path("accessToken").asText();
        ResponseEntity<String> intruder = call(HttpMethod.GET, "/api/v1/bookings/" + reference, null, priyaToken);
        assertThat(intruder.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Owner sees it under their location; admin sees it globally.
        String ownerToken = login("owner1@dev.local", "Password123!");
        ResponseEntity<String> ownerView = call(HttpMethod.GET,
                "/api/v1/owner/bookings/" + reference, null, ownerToken);
        assertThat(ownerView.getStatusCode()).isEqualTo(HttpStatus.OK);

        String adminToken = login("admin@smartpark.local", "ChangeMe123!");
        ResponseEntity<String> adminView = call(HttpMethod.GET,
                "/api/v1/admin/bookings?status=PENDING_PAYMENT", null, adminToken);
        assertThat(adminView.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(adminView.getBody()).path("data").path("totalElements").asInt())
                .isGreaterThan(0);
    }

    private String login(String email, String password) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", password), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return objectMapper.readTree(response.getBody())
                .path("data").path("tokens").path("accessToken").asText();
    }
}
