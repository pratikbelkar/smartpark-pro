package com.smartpark.support;

import com.smartpark.common.ApiResponse;
import com.smartpark.exception.ApiException;
import com.smartpark.security.AuthUser;
import com.smartpark.user.User;
import com.smartpark.user.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
public class SupportController {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<?> open(@AuthenticationPrincipal AuthUser user, @RequestBody TicketRequest request) {
        User requester = userRepository.findById(user.getId())
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        SupportTicket ticket = new SupportTicket();
        ticket.setRequester(requester);
        ticket.setSubject(request.getSubject().trim());
        ticket.setBody(request.getBody().trim());
        ticket = ticketRepository.save(ticket);
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponse.map("id", ticket.getId(), "status", ticket.getStatus()), "Support request received."));
    }

    @Data
    public static class TicketRequest {
        @NotBlank(message = "Subject is required")
        @Size(max = 200)
        private String subject;
        @NotBlank(message = "Describe the issue")
        private String body;
    }
}
