package com.claimflow.notification;

import com.claimflow.notification.NotificationService.NotificationFeed;
import com.claimflow.security.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationFeed feed(@AuthenticationPrincipal AuthUser user) {
        return service.feed(user.id());
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        service.markRead(user.id(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@AuthenticationPrincipal AuthUser user) {
        service.markAllRead(user.id());
    }
}
