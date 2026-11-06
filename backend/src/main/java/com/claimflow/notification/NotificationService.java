package com.claimflow.notification;

import com.claimflow.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void notify(Long userId, Long claimId, String message) {
        if (userId != null) {
            repo.save(new Notification(userId, claimId, message));
        }
    }

    public record NotificationDto(Long id, Long claimId, String message, boolean read, Instant createdAt) {
        static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getClaimId(), n.getMessage(), n.isRead(), n.getCreatedAt());
        }
    }

    public record NotificationFeed(long unread, List<NotificationDto> items) {}

    @Transactional(readOnly = true)
    public NotificationFeed feed(Long userId) {
        List<NotificationDto> items = repo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 30))
                .stream().map(NotificationDto::from).toList();
        return new NotificationFeed(repo.countByUserIdAndReadFalse(userId), items);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        Notification n = repo.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repo.markAllRead(userId);
    }
}
