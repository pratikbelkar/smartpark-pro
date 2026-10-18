package com.smartpark.payment;

import java.util.Map;

/**
 * Razorpay operations behind an interface: production uses the real SDK
 * ({@link RazorpayGatewayImpl}); tests mock this boundary. No fake payments
 * exist in production code paths.
 */
public interface RazorpayGateway {

    record GatewayOrder(String id, long amountPaise, String currency, String receipt) {
    }

    record AuthoritativePayment(String id, String status, String method, long amountPaise) {
    }

    record GatewayRefund(String id) {
    }

    GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    AuthoritativePayment fetchPayment(String paymentId);

    AuthoritativePayment capturePayment(String paymentId, long amountPaise);

    GatewayRefund refund(String paymentId, long amountPaise, Map<String, String> notes);

    /** Throws on mismatch. */
    void verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** Throws on mismatch. */
    void verifyWebhookSignature(String payload, String signature);
}
