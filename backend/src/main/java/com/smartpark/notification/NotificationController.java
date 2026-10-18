package com.smartpark.notification;

import com.smartpark.common.ApiResponse;
import com.smartpark.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal AuthUser user,
                                  @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                ApiResponse.map(
                        "items", notificationService.forUser(user.getId(), pageable).map(n -> ApiResponse.map(
                                "id", n.getId(),
                                "title", n.getTitle(),
                                "message", n.getMessage(),
                                "notificationType", n.getNotificationType(),
                                "read", n.isRead(),
                                "createdAt", n.getCreatedAt())),
                        "unread", notificationService.unreadCount(user.getId())),
                "Notifications loaded."));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> read(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        notificationService.markRead(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Marked as read."));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> readAll(@AuthenticationPrincipal AuthUser user) {
        notificationService.forUser(user.getId(),
                org.springframework.data.domain.Pageable.unpaged()).forEach(n -> {
                    if (!n.isRead()) {
                        notificationService.markRead(n.getId(), user.getId());
                    }
                });
        return ResponseEntity.ok(ApiResponse.ok(null, "All notifications marked as read."));
    }
}
