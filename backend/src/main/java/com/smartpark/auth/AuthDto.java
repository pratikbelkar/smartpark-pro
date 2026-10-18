package com.smartpark.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

public final class AuthDto {

    private AuthDto() {
    }

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        private String fullName;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        private String email;

        @Size(max = 20, message = "Phone must be at most 20 characters")
        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        private String password;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        private String email;

        @NotBlank(message = "Password is required")
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }

    @Data
    public static class TokenPair {
        private final String accessToken;
        private final String refreshToken;
        private final long expiresInMs;
    }

    @Data
    public static class AuthResponse {
        private final TokenPair tokens;
        private final UserResponse user;
    }

    @Data
    public static class UserResponse {
        private final UUID id;
        private final String fullName;
        private final String email;
        private final String phone;
        private final List<String> roles;
        private final boolean emailVerified;
    }
}
