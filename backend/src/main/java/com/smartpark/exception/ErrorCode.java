package com.smartpark.exception;

/** Machine-readable error codes returned in the response envelope. */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String CONFLICT = "CONFLICT";
    public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";
    public static final String BOOKING_CONFLICT = "BOOKING_CONFLICT";
    public static final String HOLD_EXPIRED = "HOLD_EXPIRED";
    public static final String PAYMENT_FAILED = "PAYMENT_FAILED";
    public static final String PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
    public static final String INVALID_QR = "INVALID_QR";
    public static final String ALREADY_CHECKED_IN = "ALREADY_CHECKED_IN";
    public static final String ALREADY_CHECKED_OUT = "ALREADY_CHECKED_OUT";
    public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";
    public static final String REFUND_UNAVAILABLE = "REFUND_UNAVAILABLE";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String GATEWAY_NOT_CONFIGURED = "GATEWAY_NOT_CONFIGURED";
    public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
}
