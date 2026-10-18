package com.smartpark.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode post(String path, Object body, String token) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        return responseData(response);
    }

    private JsonNode responseData(ResponseEntity<String> response) throws Exception {
        JsonNode root = objectMapper.readTree(response.getBody());
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("expected 2xx but got %s: %s".formatted(response.getStatusCode(), response.getBody()))
                .isTrue();
        assertThat(root.path("ok").asBoolean()).isTrue();
        return root.path("data");
    }

    @Test
    void fullAuthLifecycle() throws Exception {
        String email = "qa-%s@test.local".formatted(UUID.randomUUID().toString().substring(0, 8));

        // Register
        JsonNode registered = post("/api/v1/auth/register",
                Map.of("fullName", "QA Customer", "email", email, "password", "Secret123!"), null);
        String access = registered.path("tokens").path("accessToken").asText();
        String refresh = registered.path("tokens").path("refreshToken").asText();
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();
        assertThat(registered.path("user").path("roles").get(0).asText()).isEqualTo("CUSTOMER");

        // Duplicate registration -> 409
        ResponseEntity<String> dup = rest.postForEntity("/api/v1/auth/register",
                Map.of("fullName", "QA Customer", "email", email, "password", "Secret123!"), String.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Login + wrong password
        JsonNode loggedIn = post("/api/v1/auth/login",
                Map.of("email", email, "password", "Secret123!"), null);
        assertThat(loggedIn.path("tokens").path("accessToken").asText()).isNotBlank();

        ResponseEntity<String> badLogin = rest.postForEntity("/api/v1/auth/login",
                Map.of("email", email, "password", "WrongPass1!"), String.class);
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // /me with and without token
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loggedIn.path("tokens").path("accessToken").asText());
        ResponseEntity<String> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(me.getBody()).path("data").path("email").asText()).isEqualTo(email);

        ResponseEntity<String> anon = rest.getForEntity("/api/v1/auth/me", String.class);
        assertThat(anon.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Refresh rotates: new pair works, old refresh token is dead
        JsonNode refreshed = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh), null);
        String refresh2 = refreshed.path("tokens").path("refreshToken").asText();
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh);

        ResponseEntity<String> replay = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", refresh), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Logout revokes the current refresh token
        post("/api/v1/auth/logout", Map.of("refreshToken", refresh2), null);
        ResponseEntity<String> afterLogout = rest.postForEntity("/api/v1/auth/refresh",
                Map.of("refreshToken", refresh2), String.class);
        assertThat(afterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Validation -> 400 with field errors
        ResponseEntity<String> invalid = rest.postForEntity("/api/v1/auth/register",
                Map.of("fullName", "", "email", "not-an-email", "password", "short"), String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode invalidBody = objectMapper.readTree(invalid.getBody());
        assertThat(invalidBody.path("ok").asBoolean()).isFalse();
        assertThat(invalidBody.path("fieldErrors").size()).isGreaterThan(0);
    }
}
