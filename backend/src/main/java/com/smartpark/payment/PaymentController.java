package com.smartpark.payment;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@AuthenticationPrincipal AuthUser user,
                                         @Valid @RequestBody PaymentDto.CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.createOrder(user.getId(), request.getBookingReference()),
                "Payment order created."));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody PaymentDto.VerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.verify(user.getId(), request), "Payment verified. Booking confirmed."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.get(user.getId(), id, user.getRoles()), "Payment loaded."));
    }

    @PostMapping("/extra")
    public ResponseEntity<?> extraOrder(@AuthenticationPrincipal AuthUser user,
                                        @Valid @RequestBody PaymentDto.CreateOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.createExtraOrder(user.getId(), request.getBookingReference()),
                "Overstay payment order created."));
    }
}
