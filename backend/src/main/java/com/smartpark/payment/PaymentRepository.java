package com.smartpark.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findByBookingId(UUID bookingId);

    java.util.List<Payment> findAllByBookingId(UUID bookingId);

    java.util.List<Payment> findByCreatedAtBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);
}
