package com.smartpark.auth;

import com.smartpark.audit.AuditService;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.notification.EmailService;
import com.smartpark.notification.NotificationService;
import com.smartpark.security.AuthUser;
import com.smartpark.security.JwtService;
import com.smartpark.user.AccountStatus;
import com.smartpark.user.RefreshToken;
import com.smartpark.user.RefreshTokenRepository;
import com.smartpark.user.Role;
import com.smartpark.user.RoleRepository;
import com.smartpark.user.Roles;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import com.smartpark.user.UserRole;
import com.smartpark.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer self-registration, login, and opaque refresh-token rotation.
 * Owner/staff accounts are provisioned by admins, never self-registered.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    @Value("${app.jwt.access-ttl-ms}")
    private long accessTtlMs;

    @Value("${app.jwt.refresh-ttl-ms}")
    private long refreshTtlMs;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "An account with this email already exists.");
        }

        User user = new User();
        user.setFullName(request.getFullName().trim());
        user.setEmail(email);
        user.setPhone(request.getPhone() == null ? null : request.getPhone().trim());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAccountStatus(AccountStatus.ACTIVE);
        user = userRepository.save(user);
        grantRole(user, Roles.CUSTOMER);

        auditService.log(user.getId(), "USER_REGISTERED", "users", user.getId().toString(),
                Map.of("email", email));
        notificationService.notify(user, "WELCOME", "Welcome to SmartPark Pro",
                "Find parking, reserve your slot in advance, and enter with a quick QR scan.");
        emailService.welcome(email, user.getFullName());

        return buildResponse(user);
    }

    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS,
                        "Incorrect email or password."));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS, "Incorrect email or password.");
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("This account has been suspended. Contact support.");
        }
        auditService.log(user.getId(), "USER_LOGIN", "users", user.getId().toString(), Map.of());
        return buildResponse(user);
    }

    /** Single-use rotation: the presented token is revoked and replaced. */
    @Transactional
    public AuthDto.TokenPair refresh(String rawToken) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .filter(t -> t.isUsable(LocalDateTime.now()))
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.INVALID_TOKEN,
                        "Session expired. Please sign in again."));
        token.setRevokedAt(LocalDateTime.now());
        User user = token.getUser();
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.forbidden("This account has been suspended. Contact support.");
        }
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByTokenHash(sha256Hex(rawToken))
                .ifPresent(token -> token.setRevokedAt(LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public AuthDto.UserResponse me(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        return toUserResponse(user, rolesOf(user));
    }

    // ------------------------------------------------------------- helpers

    private AuthDto.AuthResponse buildResponse(User user) {
        List<String> roles = rolesOf(user);
        AuthDto.TokenPair tokens = issueTokens(user);
        return new AuthDto.AuthResponse(tokens, toUserResponse(user, roles));
    }

    private AuthDto.TokenPair issueTokens(User user) {
        List<String> roles = rolesOf(user);
        String access = jwtService.generateAccess(
                new AuthUser(user.getId(), user.getEmail(), user.getFullName(), roles));

        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256Hex(rawRefresh));
        token.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTtlMs / 1000));
        refreshTokenRepository.save(token);

        return new AuthDto.TokenPair(access, rawRefresh, accessTtlMs);
    }

    private void grantRole(User user, String roleName) {
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role missing: " + roleName));
        UserRole link = new UserRole();
        link.setUser(user);
        link.setRole(role);
        userRoleRepository.save(link);
    }

    private List<String> rolesOf(User user) {
        return userRoleRepository.findRoleNamesByUserId(user.getId());
    }

    private AuthDto.UserResponse toUserResponse(User user, List<String> roles) {
        return new AuthDto.UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), roles, user.isEmailVerified());
    }

    static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
