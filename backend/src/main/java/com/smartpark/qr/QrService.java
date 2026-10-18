package com.smartpark.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * QR passes carry an <b>opaque token only</b> — no personal data.
 *
 * The token is {@code Base64URL(HMAC(qrSecret, bookingId + ":" + passId))}:
 * unguessable, recomputable for display without storing secrets, revocable
 * via {@code revoked_at}, and always verified server-side (booking + payment
 * + time window) on every scan.
 */
@Service
public class QrService {

    private final QrPassRepository passRepository;
    private final ParkingBookingRepository bookingRepository;
    private final String qrSecret;

    public QrService(QrPassRepository passRepository,
                     ParkingBookingRepository bookingRepository,
                     @Value("${app.qr.secret:smartpark-dev-qr-secret-change-in-prod}") String qrSecret) {
        this.passRepository = passRepository;
        this.bookingRepository = bookingRepository;
        this.qrSecret = qrSecret;
    }

    /** Issued once per confirmed booking; idempotent. */
    @Transactional
    public IssuedPass issue(UUID bookingId) {
        QrPass pass = passRepository.findByBookingId(bookingId)
                .filter(p -> p.getRevokedAt() == null)
                .orElseGet(() -> {
                    ParkingBooking booking = bookingRepository.findById(bookingId)
                            .orElseThrow(() -> ApiException.notFound("Booking not found."));
                    QrPass created = new QrPass();
                    created.setBooking(booking);
                    created.setTokenHash("pending");
                    created.setExpiresAt(booking.getEndTime().plusHours(3));
                    created = passRepository.save(created);
                    created.setTokenHash(sha256Hex(deriveToken(booking.getId(), created.getId())));
                    return created;
                });
        return new IssuedPass(pass, deriveToken(pass.getBooking().getId(), pass.getId()));
    }

    /** Display token for the customer's ticket (same value until revoked). */
    @Transactional(readOnly = true)
    public IssuedPass display(UUID bookingId) {
        QrPass pass = passRepository.findByBookingId(bookingId)
                .filter(p -> p.getRevokedAt() == null && p.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> ApiException.notFound("QR pass not available for this booking."));
        return new IssuedPass(pass, deriveToken(pass.getBooking().getId(), pass.getId()));
    }

    /** Validates a presented token and returns the live pass. */
    @Transactional(readOnly = true)
    public QrPass verifyToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw ApiException.notFound("Invalid QR code.");
        }
        QrPass pass = passRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .orElseThrow(() -> ApiException.notFound("Invalid QR code."));
        if (pass.getRevokedAt() != null) {
            throw ApiException.conflict(ErrorCode.INVALID_QR, "This QR pass has been revoked.");
        }
        if (pass.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.conflict(ErrorCode.INVALID_QR, "This QR pass has expired.");
        }
        return pass;
    }

    @Transactional
    public void revoke(UUID bookingId) {
        passRepository.findByBookingId(bookingId).ifPresent(pass -> {
            if (pass.getRevokedAt() == null) {
                pass.setRevokedAt(LocalDateTime.now());
            }
        });
    }

    /** Renders the opaque token as a PNG. */
    public byte[] renderPng(String token, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(500, ErrorCode.INTERNAL_ERROR, "Could not render the QR pass.");
        }
    }

    public String renderDataUri(String token, int size) {
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(renderPng(token, size));
    }

    private String deriveToken(UUID bookingId, UUID passId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(qrSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((bookingId + ":" + passId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC unavailable", ex);
        }
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

    /** A pass together with its display token. */
    public record IssuedPass(QrPass pass, String rawToken) {
    }
}
