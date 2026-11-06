package com.claimflow.claim;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "claim_comments")
public class ClaimComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private String body;

    /** Internal notes are only visible to staff. */
    @Column(nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ClaimComment() {}

    public ClaimComment(Long claimId, Long authorId, String body, boolean internal) {
        this.claimId = claimId;
        this.authorId = authorId;
        this.body = body;
        this.internal = internal;
    }

    public Long getId() { return id; }
    public Long getClaimId() { return claimId; }
    public Long getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isInternal() { return internal; }
    public Instant getCreatedAt() { return createdAt; }
}
