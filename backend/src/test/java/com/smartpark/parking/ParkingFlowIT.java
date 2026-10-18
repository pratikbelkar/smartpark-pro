package com.smartpark.parking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartpark.booking.BookingHold;
import com.smartpark.booking.BookingHoldRepository;
import com.smartpark.booking.HoldStatus;
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
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class ParkingFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BookingHoldRepository holdRepository;

    @Autowired
    private ParkingSlotRepository slotRepository;

    @Autowired
    private ParkingLocationRepository locationRepository;

    @Autowired
    private UserRepository userRepository;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

    @Test
    @org.junit.jupiter.api.Order(1)
    void seedSearchAndAvailability() throws Exception {
        // 3 seeded Pune locations with paise pricing on cards
        JsonNode page = data(call(HttpMethod.GET,
                "/api/v1/parking-locations?city=Pune&size=20", null, null), HttpStatus.OK);
        assertThat(page.path("totalElements").asInt()).isEqualTo(3);
        JsonNode cityCentre = null;
        for (JsonNode card : page.path("content")) {
            if (card.path("parkingName").asText().equals("City Centre Parking")) {
                cityCentre = card;
            }
        }
        assertThat(cityCentre).isNotNull();
        assertThat(cityCentre.path("hourlyRates").path("CAR").asLong()).isEqualTo(5000L);
        UUID locationId = UUID.fromString(cityCentre.path("id").asText());

        // Availability over tomorrow evening: everything free
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(17).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(3);
        String window = "?start=%s&end=%s".formatted(start.format(ISO), end.format(ISO));
        JsonNode availability = data(call(HttpMethod.GET,
                "/api/v1/parking-locations/" + locationId + "/availability" + window, null, null), HttpStatus.OK);
        assertThat(availability.path("freeSlots").asLong()).isEqualTo(60L);

        // An ACTIVE hold blocks exactly one slot
        ParkingSlot slot = slotRepository
                .findByLocationIdAndVehicleTypeAndOperationalStatusOrderBySlotNumberAsc(
                        locationId, VehicleType.CAR, SlotOperationalStatus.AVAILABLE).get(0);
        BookingHold hold = new BookingHold();
        hold.setCustomer(userRepository.findByEmailIgnoreCase("rahul@dev.local").orElseThrow());
        hold.setLocation(locationRepository.findById(locationId).orElseThrow());
        hold.setSlot(slot);
        hold.setStartTime(start);
        hold.setEndTime(end);
        hold.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        hold.setStatus(HoldStatus.ACTIVE);
        holdRepository.save(hold);

        JsonNode blocked = data(call(HttpMethod.GET,
                "/api/v1/parking-locations/" + locationId + "/availability" + window, null, null), HttpStatus.OK);
        assertThat(blocked.path("freeSlots").asLong()).isEqualTo(59L);
        long reserved = 0;
        for (JsonNode floor : blocked.path("floors")) {
            for (JsonNode zone : floor.path("zones")) {
                for (JsonNode s : zone.path("slots")) {
                    if (s.path("state").asText().equals("RESERVED")) {
                        reserved++;
                        assertThat(s.path("slotNumber").asText()).isEqualTo(slot.getSlotNumber());
                    }
                }
            }
        }
        assertThat(reserved).isEqualTo(1L);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void publicSearchWithoutCity() throws Exception {
        // Null-city path once crashed on lower(bytea) binding (regression).
        JsonNode page = data(call(HttpMethod.GET,
                "/api/v1/parking-locations?size=20", null, null), HttpStatus.OK);
        assertThat(page.path("totalElements").asInt()).isEqualTo(3);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    void ownerIsolationAndOnboarding() throws Exception {
        String owner1 = login("owner1@dev.local", "Password123!");
        String owner2 = login("owner2@dev.local", "Password123!");

        UUID cityCentre = locationRepository
                .searchPublic("Pune", org.springframework.data.domain.PageRequest.of(0, 20)).getContent().stream()
                .filter(l -> l.getParkingName().equals("City Centre Parking"))
                .findFirst().orElseThrow().getId();

        // owner2 cannot touch owner1's location
        ResponseEntity<String> forbidden = call(HttpMethod.PUT,
                "/api/v1/owner/parking-locations/" + cityCentre,
                Map.of("parkingName", "Hijacked", "fullAddress", "X", "city", "Pune", "totalCapacity", 0),
                owner2);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // anonymous owner write -> 401
        ResponseEntity<String> anon = call(HttpMethod.POST, "/api/v1/owner/parking-locations",
                Map.of("parkingName", "X", "fullAddress", "Y", "city", "Pune", "totalCapacity", 0), null);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // owner1 manages own location: add floor + zone + bulk slots
        JsonNode floor = data(call(HttpMethod.POST,
                "/api/v1/owner/parking-locations/" + cityCentre + "/floors",
                Map.of("floorName", "P3", "floorNumber", 3), owner1), HttpStatus.OK);
        JsonNode zone = data(call(HttpMethod.POST, "/api/v1/owner/zones",
                Map.of("floorId", floor.path("id").asText(), "zoneName", "D"), owner1), HttpStatus.OK);
        JsonNode bulk = data(call(HttpMethod.POST,
                "/api/v1/owner/parking-locations/" + cityCentre + "/slots/bulk",
                Map.of("floorId", floor.path("id").asText(), "zoneId", zone.path("id").asText(),
                        "vehicleType", "CAR", "prefix", "T", "startNumber", 1, "count", 3), owner1),
                HttpStatus.OK);
        assertThat(bulk.size()).isEqualTo(3);

        // New customer onboards as owner (PENDING), admin approves, owner builds
        String email = "newowner-%s@test.local".formatted(UUID.randomUUID().toString().substring(0, 8));
        ResponseEntity<String> registered = rest.postForEntity("/api/v1/auth/register",
                Map.of("fullName", "New Owner", "email", email, "password", "Secret123!"), String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        String ownerToken = objectMapper.readTree(registered.getBody())
                .path("data").path("tokens").path("accessToken").asText();

        JsonNode onboarded = data(call(HttpMethod.POST, "/api/v1/owner/onboard",
                Map.of("businessName", "Fresh Parking"), ownerToken), HttpStatus.OK);
        assertThat(onboarded.path("verificationStatus").asText()).isEqualTo("PENDING");
        UUID ownerId = UUID.fromString(onboarded.path("id").asText());

        // still PENDING -> cannot create locations
        ResponseEntity<String> pending = call(HttpMethod.POST, "/api/v1/owner/parking-locations",
                Map.of("parkingName", "X", "fullAddress", "Y", "city", "Pune", "totalCapacity", 0), ownerToken);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String admin = login("admin@smartpark.local", "ChangeMe123!");
        JsonNode approved = data(call(HttpMethod.PUT, "/api/v1/admin/owners/" + ownerId + "/status",
                Map.of("status", "APPROVED"), admin), HttpStatus.OK);
        assertThat(approved.path("verificationStatus").asText()).isEqualTo("APPROVED");

        // Roles are snapshotted in the JWT: sign in again for the new PARKING_OWNER role.
        String approvedOwnerToken = login(email, "Secret123!");

        JsonNode created = data(call(HttpMethod.POST, "/api/v1/owner/parking-locations",
                Map.of("parkingName", "Fresh Lot", "fullAddress", "1 New St", "city", "Mumbai", "totalCapacity", 10),
                approvedOwnerToken), HttpStatus.OK);
        assertThat(created.path("parkingName").asText()).isEqualTo("Fresh Lot");
    }
}
