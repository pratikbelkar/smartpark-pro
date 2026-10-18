package com.smartpark.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Transactional email fan-out (Mailpit in dev, real SMTP in prod).
 * Every send is best-effort and asynchronous — mail must never break a booking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@smartpark.local}")
    private String from;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Async
    public void welcome(String to, String name) {
        send(to, "Welcome to SmartPark Pro",
                "<p>Hi %s,</p><p>Find parking, reserve your slot in advance, and enter with a quick QR scan.</p>"
                        .formatted(escape(name)));
    }

    @Async
    public void bookingConfirmed(String to, String reference, String slot, String location,
                                 String start, String end, String total) {
        send(to, "Booking confirmed: " + reference,
                """
                <p>Booking <b>%s</b> is confirmed.</p>
                <ul><li>Parking: %s</li><li>Slot: %s</li><li>Entry: %s</li><li>Exit: %s</li><li>Total: %s</li></ul>
                <p>Your QR pass: <a href="%s/bookings/%s">open ticket</a></p>
                """.formatted(reference, escape(location), escape(slot), start, end, total,
                        frontendUrl, reference));
    }

    @Async
    public void bookingCancelled(String to, String reference, String refundLine) {
        send(to, "Booking cancelled: " + reference,
                "<p>Booking <b>%s</b> was cancelled.</p><p>%s</p>"
                        .formatted(reference, escape(refundLine)));
    }

    @Async
    public void refundProcessed(String to, String reference, String amount) {
        send(to, "Refund processed: " + reference,
                "<p>Refund of <b>%s</b> for booking <b>%s</b> is on its way to the original payment method.</p>"
                        .formatted(amount, reference));
    }

    @Async
    public void checkoutReceipt(String to, String reference, String duration, String extra) {
        send(to, "Parking receipt: " + reference,
                "<p>Thanks for parking with us.</p><p>Booking <b>%s</b> · parked %s%s</p>"
                        .formatted(reference, escape(duration),
                                extra == null ? "" : " · overstay " + escape(extra)));
    }

    private void send(String to, String subject, String html) {
        if (to == null || to.isBlank()) {
            return;
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("[SmartPark Pro] " + subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("Email to {} failed (subject: {}).", to, subject, ex);
        }
    }

    private String escape(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
