package com.smartpark.exception;

import com.smartpark.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Structured errors: {ok:false, code, message, timestamp, requestId, fieldErrors}.
 * Never leaks stack traces or secrets to clients.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse.Response> handleApi(ApiException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("API error {} {} -> {} {}", request.getMethod(), request.getRequestURI(),
                ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage(), requestId, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse.Response> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        e -> e.getField(),
                        e -> e.getDefaultMessage() == null ? "Invalid value" : e.getDefaultMessage(),
                        (a, b) -> a, java.util.LinkedHashMap::new));
        return ResponseEntity.badRequest().body(ApiResponse.error(
                ErrorCode.VALIDATION_FAILED, "Request validation failed.", requestId(request), fields));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse.Response> handleBadRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                ErrorCode.BAD_REQUEST, "Malformed request body.", requestId(request), null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse.Response> handleConflict(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        boolean overlap = message.contains("no_overlapping_holds") || message.contains("no_overlapping_bookings");
        String code = overlap ? ErrorCode.SLOT_UNAVAILABLE : ErrorCode.CONFLICT;
        String userMessage = overlap
                ? "That slot was just booked for the selected time. Please choose another slot or time."
                : "This record conflicts with existing data.";
        log.warn("DB conflict {} {} -> {}", request.getMethod(), request.getRequestURI(), code);
        return ResponseEntity.status(409).body(ApiResponse.error(code, userMessage, requestId(request), null));
    }

    @ExceptionHandler({AccessDeniedException.class})
    public ResponseEntity<ApiResponse.Response> handleForbidden(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(403).body(ApiResponse.error(
                ErrorCode.FORBIDDEN, "You do not have permission to perform this action.",
                requestId(request), null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse.Response> handleAuth(AuthenticationException ex,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(401).body(ApiResponse.error(
                ErrorCode.UNAUTHORIZED, "Authentication required. Please sign in.",
                requestId(request), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse.Response> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled error {} {} [{}]", request.getMethod(), request.getRequestURI(), requestId, ex);
        return ResponseEntity.status(500).body(ApiResponse.error(
                ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.", requestId, null));
    }

    private String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute("requestId");
        if (existing != null) {
            return existing.toString();
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        request.setAttribute("requestId", id);
        return id;
    }
}
