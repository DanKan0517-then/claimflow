package com.claimflow.claim;

import com.claimflow.policy.PolicyType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ClaimDtos {
    private ClaimDtos() {}

    public record CreateClaimRequest(
            @NotNull Long policyId,
            @NotBlank @Size(min = 5, max = 160) String title,
            @NotBlank @Size(min = 20, max = 5000, message = "Describe what happened in at least 20 characters") String description,
            @NotNull @PastOrPresent(message = "Incident date can't be in the future") LocalDate incidentDate,
            @NotNull @DecimalMin(value = "1", message = "Amount must be positive") @Digits(integer = 12, fraction = 2) BigDecimal claimedAmount) {}

    public record ActionRequest(
            @NotNull ClaimAction action,
            @DecimalMin(value = "1") BigDecimal approvedAmount,
            @Size(max = 2000) String reason,
            Long expectedVersion) {}

    public record CommentRequest(@NotBlank @Size(max = 4000) String body, boolean internal) {}

    public record RespondRequest(@NotBlank @Size(max = 4000) String message) {}

    public record ClaimSummaryDto(
            Long id, String claimNumber, String title, PolicyType type, ClaimStatus status,
            BigDecimal claimedAmount, BigDecimal approvedAmount, Integer fraudScore, String riskLevel,
            Long customerId, String customerName, Long assignedAdjusterId, String adjusterName,
            Instant slaDueAt, boolean slaBreached, Instant createdAt, Instant updatedAt) {}

    public record PolicyBrief(Long id, String policyNumber, PolicyType type, BigDecimal sumInsured,
                              LocalDate startDate, LocalDate endDate) {}

    public record DocumentDto(Long id, String fileName, String contentType, long sizeBytes, boolean textExtracted,
                              Instant uploadedAt) {}

    public record CommentDto(Long id, Long authorId, String authorName, String authorRole, String body,
                             boolean internal, Instant createdAt) {}

    public record AuditDto(Long id, String action, String fromStatus, String toStatus, String details,
                           String actorName, Instant createdAt) {}

    public record ClaimDetailDto(
            ClaimSummaryDto claim,
            String description,
            LocalDate incidentDate,
            PolicyBrief policy,
            String decisionReason,
            Instant decidedAt,
            Instant paidAt,
            // AI + fraud details are staff-only (null for customers)
            String aiSummary,
            Map<String, Object> aiExtracted,
            String aiProvider,
            List<String> fraudFlags,
            List<DocumentDto> documents,
            List<CommentDto> comments,
            List<AuditDto> timeline,
            List<ClaimAction> allowedActions,
            long version) {}

    public static String riskLevel(Integer score) {
        if (score == null) return null;
        return score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
    }
}
