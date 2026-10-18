package com.smartpark.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.health.mail.enabled=false"})
@Testcontainers
class EmailFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void welcomeEmailSentOnRegistration() {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));

        String email = "mail-%s@test.local".formatted(UUID.randomUUID().toString().substring(0, 8));
        ResponseEntity<String> response = rest.postForEntity("/api/v1/auth/register",
                Map.of("fullName", "Mail User", "email", email, "password", "Secret123!"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        // @Async delivery: allow a few seconds, then assert exactly one send.
        verify(mailSender, timeout(5000).times(1)).send(any(MimeMessage.class));
    }
}
