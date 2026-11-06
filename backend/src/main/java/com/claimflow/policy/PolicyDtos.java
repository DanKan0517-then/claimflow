package com.claimflow.policy;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PolicyDtos {
    private PolicyDtos() {}

    public record PolicyDto(Long id, String policyNumber, Long holderId, String holderName, PolicyType type,
                            BigDecimal sumInsured, BigDecimal premium, LocalDate startDate, LocalDate endDate,
                            PolicyStatus status) {
        public static PolicyDto from(Policy p, String holderName) {
            return new PolicyDto(p.getId(), p.getPolicyNumber(), p.getHolderId(), holderName, p.getType(),
                    p.getSumInsured(), p.getPremium(), p.getStartDate(), p.getEndDate(), p.getStatus());
        }
    }

    public record CreatePolicyRequest(
            @NotNull Long holderId,
            @NotNull PolicyType type,
            @NotNull @DecimalMin(value = "1000", message = "Sum insured must be at least 1000") BigDecimal sumInsured,
            @NotNull @PositiveOrZero BigDecimal premium,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {}
}
