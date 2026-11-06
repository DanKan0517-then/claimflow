package com.claimflow.claim;

import com.claimflow.policy.PolicyType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @Column(name = "policy_id", nullable = false)
    private Long policyId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "assigned_adjuster_id")
    private Long assignedAdjusterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status = ClaimStatus.DRAFT;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "claimed_amount", nullable = false)
    private BigDecimal claimedAmount;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "fraud_score")
    private Integer fraudScore;

    /** Newline separated list of human readable risk reasons. */
    @Column(name = "fraud_flags")
    private String fraudFlags;

    @Column(name = "ai_summary")
    private String aiSummary;

    /** JSON of fields the AI pulled out of the description and documents. */
    @Column(name = "ai_extracted")
    private String aiExtracted;

    @Column(name = "ai_provider")
    private String aiProvider;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Optimistic locking: two adjusters acting on the same claim at once -> second one gets 409. */
    @Version
    private long version;

    protected Claim() {}

    public Claim(String claimNumber, Long policyId, Long customerId, PolicyType type, String title,
                 String description, LocalDate incidentDate, BigDecimal claimedAmount) {
        this.claimNumber = claimNumber;
        this.policyId = policyId;
        this.customerId = customerId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.incidentDate = incidentDate;
        this.claimedAmount = claimedAmount;
        this.slaDueAt = createdAt.plus(type.sla());
    }

    /** Moves the claim to a new state, enforcing the state machine. Returns the previous state. */
    public ClaimStatus transitionTo(ClaimStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Cannot move claim from " + status + " to " + next);
        }
        ClaimStatus previous = status;
        status = next;
        if (next == ClaimStatus.APPROVED || next == ClaimStatus.REJECTED) {
            decidedAt = Instant.now();
        }
        if (next == ClaimStatus.PAID) {
            paidAt = Instant.now();
        }
        return previous;
    }

    public boolean isSlaBreached() {
        Instant end = decidedAt != null ? decidedAt : Instant.now();
        return status != ClaimStatus.DRAFT && end.isAfter(slaDueAt);
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public Long getPolicyId() { return policyId; }
    public Long getCustomerId() { return customerId; }
    public Long getAssignedAdjusterId() { return assignedAdjusterId; }
    public void setAssignedAdjusterId(Long id) { this.assignedAdjusterId = id; }
    public PolicyType getType() { return type; }
    public ClaimStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public Integer getFraudScore() { return fraudScore; }
    public void setFraudScore(Integer fraudScore) { this.fraudScore = fraudScore; }
    public String getFraudFlags() { return fraudFlags; }
    public void setFraudFlags(String fraudFlags) { this.fraudFlags = fraudFlags; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public String getAiExtracted() { return aiExtracted; }
    public void setAiExtracted(String aiExtracted) { this.aiExtracted = aiExtracted; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public void setSlaDueAt(Instant slaDueAt) { this.slaDueAt = slaDueAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
