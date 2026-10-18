package com.smartpark.common;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Uniform API envelope. Success: {ok:true, code, message, data, ...}.
 * Errors use the same shape with ok:false and optional fieldErrors.
 */
public final class ApiResponse {

    private ApiResponse() {
    }

    public static Response ok(Object data, String message) {
        return new Response(true, "OK", message, data, null, LocalDateTime.now(), null);
    }

    public static Response ok(Object data) {
        return ok(data, "OK");
    }

    public static Response error(String code, String message, String requestId,
                                 Map<String, String> fieldErrors) {
        return new Response(false, code, message, null, fieldErrors, LocalDateTime.now(), requestId);
    }

    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    public record Response(boolean ok, String code, String message, Object data,
                           Map<String, String> fieldErrors, LocalDateTime timestamp, String requestId) {
    }
}
