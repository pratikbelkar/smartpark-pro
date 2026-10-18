package com.smartpark.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

public final class PaymentDto {

    private PaymentDto() {
    }

    @Data
    public static class CreateOrderRequest {
        @NotBlank(message = "Booking reference is required")
        private String bookingReference;
    }

    @Data
    public static class VerifyRequest {
        @NotBlank(message = "Order id is required")
        private String razorpayOrderId;
        @NotBlank(message = "Payment id is required")
        private String razorpayPaymentId;
        @NotBlank(message = "Signature is required")
        private String razorpaySignature;
    }

    @Data
    public static class OrderResponse {
        private final String keyId;
        private final String orderId;
        private final long amountPaise;
        private final String currency;
        private final String bookingReference;
    }

    @Data
    public static class PaymentResponse {
        private Long id;
        private String bookingReference;
        private String paymentStatus;
        private String paymentMethod;
        private long amountPaise;
        private String currency;
        private String razorpayOrderId;
        private String razorpayPaymentId;
    }

    @Data
    public static class VerifyResponse {
        private final String bookingReference;
        private final String bookingStatus;
        private final String paymentStatus;
    }

    @Data
    public static class RefundResponse {
        private Long id;
        private String bookingReference;
        private long amountPaise;
        private String status;
        private String razorpayRefundId;
        private String reason;
    }
}
