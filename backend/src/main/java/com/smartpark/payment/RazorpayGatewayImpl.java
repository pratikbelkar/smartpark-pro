package com.smartpark.payment;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Live Razorpay SDK calls. Fails fast with 503 when test keys are absent. */
@Slf4j
@Service
public class RazorpayGatewayImpl implements RazorpayGateway {

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayGatewayImpl(
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret,
            @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
    }

    public String keyId() {
        return keyId;
    }

    @Override
    public GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", currency);
            request.put("receipt", receipt);
            request.put("notes", notes == null ? Map.of() : notes);
            com.razorpay.Order order = client.orders.create(request);
            return new GatewayOrder(order.get("id"), amountPaise, currency, receipt);
        } catch (RazorpayException ex) {
            log.error("Razorpay order creation failed for receipt {}", receipt, ex);
            throw ApiException.badRequest(ErrorCode.PAYMENT_FAILED,
                    "Payment gateway rejected the order. Please retry.");
        }
    }

    @Override
    public AuthoritativePayment fetchPayment(String paymentId) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            com.razorpay.Payment payment = client.payments.fetch(paymentId);
            return new AuthoritativePayment(payment.get("id"),
                    String.valueOf(payment.get("status")),
                    payment.has("method") ? String.valueOf(payment.get("method")) : null,
                    Long.parseLong(String.valueOf(payment.get("amount"))));
        } catch (RazorpayException ex) {
            log.error("Razorpay payment fetch failed for {}", paymentId, ex);
            throw ApiException.badRequest(ErrorCode.PAYMENT_FAILED,
                    "Could not verify the payment with the gateway.");
        }
    }

    @Override
    public AuthoritativePayment capturePayment(String paymentId, long amountPaise) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            request.put("currency", "INR");
            com.razorpay.Payment payment = client.payments.capture(paymentId, request);
            return new AuthoritativePayment(payment.get("id"),
                    String.valueOf(payment.get("status")),
                    payment.has("method") ? String.valueOf(payment.get("method")) : null,
                    Long.parseLong(String.valueOf(payment.get("amount"))));
        } catch (RazorpayException ex) {
            log.error("Razorpay capture failed for {}", paymentId, ex);
            throw ApiException.badRequest(ErrorCode.PAYMENT_FAILED,
                    "Payment authorization could not be captured. No amount was charged.");
        }
    }

    @Override
    public GatewayRefund refund(String paymentId, long amountPaise, Map<String, String> notes) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject request = new JSONObject();
            request.put("amount", amountPaise);
            if (notes != null && !notes.isEmpty()) {
                request.put("notes", notes);
            }
            com.razorpay.Refund refund = client.payments.refund(paymentId, request);
            return new GatewayRefund(refund.get("id"));
        } catch (RazorpayException ex) {
            log.error("Razorpay refund failed for {}", paymentId, ex);
            throw ApiException.badRequest(ErrorCode.REFUND_UNAVAILABLE,
                    "Gateway refund failed. It will be retried automatically.");
        }
    }

    @Override
    public void verifyPaymentSignature(String orderId, String paymentId, String signature) {
        requireKeys();
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("razorpay_order_id", orderId);
            attributes.put("razorpay_payment_id", paymentId);
            attributes.put("razorpay_signature", signature);
            // NOTE: the SDK returns boolean (false on mismatch) — it does NOT
            // throw for a bad signature, so the return value must be checked.
            if (!Utils.verifyPaymentSignature(attributes, keySecret)) {
                throw ApiException.badRequest(ErrorCode.INVALID_SIGNATURE,
                        "Payment signature mismatch. The payment was not accepted.");
            }
        } catch (RazorpayException ex) {
            throw ApiException.badRequest(ErrorCode.INVALID_SIGNATURE,
                    "Payment signature mismatch. The payment was not accepted.");
        }
    }

    @Override
    public void verifyWebhookSignature(String payload, String signature) {
        if (webhookSecret.isBlank()) {
            throw new ApiException(503, ErrorCode.GATEWAY_NOT_CONFIGURED,
                    "Webhook secret is not configured.");
        }
        try {
            // NOTE: boolean return (see above) — a forged signature yields false.
            if (!Utils.verifyWebhookSignature(payload, signature, webhookSecret)) {
                throw ApiException.unauthorized(ErrorCode.INVALID_SIGNATURE, "Invalid webhook signature.");
            }
        } catch (RazorpayException ex) {
            throw ApiException.unauthorized(ErrorCode.INVALID_SIGNATURE, "Invalid webhook signature.");
        }
    }

    private void requireKeys() {
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new ApiException(503, ErrorCode.GATEWAY_NOT_CONFIGURED,
                    "Online payments are not configured yet. Contact support.");
        }
    }
}
