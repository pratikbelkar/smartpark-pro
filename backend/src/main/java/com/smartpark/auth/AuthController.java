package com.smartpark.auth;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(request), "Account created successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request), "Signed in successfully."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody AuthDto.RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponse.map("tokens", authService.refresh(request.getRefreshToken())), "Session refreshed."));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@Valid @RequestBody AuthDto.RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Signed out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal AuthUser user) {
        if (user == null) {
            throw com.smartpark.exception.ApiException.unauthorized(
                    com.smartpark.exception.ErrorCode.UNAUTHORIZED, "Authentication required. Please sign in.");
        }
        return ResponseEntity.ok(ApiResponse.ok(authService.me(user.getId()), "Profile loaded."));
    }
}
