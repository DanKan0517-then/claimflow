package com.claimflow.policy;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(name = "holder_id", nullable = false)
    private Long holderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyType type;

    @Column(name = "sum_insured", nullable = false)
    private BigDecimal sumInsured;

    @Column(nullable = false)
    private BigDecimal premium;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Policy() {}

    public Policy(String policyNumber, Long holderId, PolicyType type, BigDecimal sumInsured, BigDecimal premium,
                  LocalDate startDate, LocalDate endDate) {
        this.policyNumber = policyNumber;
        this.holderId = holderId;
        this.type = type;
        this.sumInsured = sumInsured;
        this.premium = premium;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /** A claim is only valid if the incident happened while the policy was in force. */
    public boolean covers(LocalDate date) {
        return status == PolicyStatus.ACTIVE && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public Long getHolderId() { return holderId; }
    public PolicyType getType() { return type; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public BigDecimal getPremium() { return premium; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public PolicyStatus getStatus() { return status; }
    public void setStatus(PolicyStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
