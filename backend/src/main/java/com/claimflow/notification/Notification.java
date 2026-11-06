package com.claimflow.notification;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(nullable = false)
    private String message;

    @Column(name = "read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {}

    public Notification(Long userId, Long claimId, String message) {
        this.userId = userId;
        this.claimId = claimId;
        this.message = message.length() > 500 ? message.substring(0, 497) + "..." : message;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getClaimId() { return claimId; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
