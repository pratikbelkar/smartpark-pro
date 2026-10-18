package com.smartpark.payment;

import com.smartpark.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Razorpay server-to-server callbacks. No authentication (gateway-signed);
 * every event is HMAC-verified and processed at most once.
 */
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaymentService paymentService;

    @PostMapping("/razorpay")
    public ResponseEntity<?> razorpay(HttpServletRequest request,
                                      @RequestHeader(value = "X-Razorpay-Signature", required = false)
                                      String signature) throws Exception {
        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(ApiResponse.ok(
                paymentService.webhook(payload, signature == null ? "" : signature), "Webhook received."));
    }
}
