package com.claimflow.audit;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false)
    private String action;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status")
    private String toStatus;

    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(Long claimId, Long actorId, String action, String fromStatus, String toStatus, String details) {
        this.claimId = claimId;
        this.actorId = actorId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.details = details;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public Long getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
