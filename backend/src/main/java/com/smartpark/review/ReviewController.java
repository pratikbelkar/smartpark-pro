package com.smartpark.review;

import com.smartpark.booking.BookingStatus;
import com.smartpark.booking.ParkingBooking;
import com.smartpark.booking.ParkingBookingRepository;
import com.smartpark.common.ApiResponse;
import com.smartpark.exception.ApiException;
import com.smartpark.exception.ErrorCode;
import com.smartpark.security.AuthUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customers review completed stays — one review per booking. */
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final ParkingBookingRepository bookingRepository;

    @PostMapping
    public ResponseEntity<?> leave(@AuthenticationPrincipal AuthUser user, @RequestBody ReviewRequest request) {
        ParkingBooking booking = bookingRepository.findByBookingReference(request.getBookingReference().trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!booking.getCustomer().getId().equals(user.getId())) {
            throw ApiException.forbidden("You do not have access to this booking.");
        }
        if (booking.getBookingStatus() != BookingStatus.COMPLETED) {
            throw ApiException.conflict(ErrorCode.BOOKING_CONFLICT, "Only completed stays can be reviewed.");
        }
        Review review = new Review();
        review.setCustomer(booking.getCustomer());
        review.setLocation(booking.getLocation());
        review.setBooking(booking);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        try {
            review = reviewRepository.save(review);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "This booking has already been reviewed.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponse.map("id", review.getId()), "Thanks for the review."));
    }

    @Data
    public static class ReviewRequest {
        @NotBlank(message = "Booking reference is required")
        private String bookingReference;
        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be 1-5")
        @Max(value = 5, message = "Rating must be 1-5")
        private Short rating;
        @Size(max = 2000)
        private String comment;
    }
}
