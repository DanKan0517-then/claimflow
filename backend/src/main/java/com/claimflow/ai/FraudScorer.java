package com.claimflow.ai;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Explainable fraud score (0-100). Every point comes with a reason the adjuster can read,
 * which matters more in insurance than a black-box number.
 */
@Component
public class FraudScorer {

    public record Signals(
            BigDecimal claimedAmount,
            BigDecimal sumInsured,
            long daysSincePolicyStart,
            long daysSinceIncident,
            long priorClaimsLast90Days,
            boolean duplicateDocument,
            int documentCount,
            BigDecimal billTotal,
            int aiRiskAdjustment,
            List<String> aiRiskReasons
    ) {}

    public record Result(int score, List<String> flags) {
        public String riskLevel() {
            return score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
        }
    }

    public Result score(Signals s) {
        int score = 0;
        List<String> flags = new ArrayList<>();

        if (s.sumInsured() != null && s.sumInsured().signum() > 0) {
            BigDecimal ratio = s.claimedAmount().divide(s.sumInsured(), 4, RoundingMode.HALF_UP);
            if (ratio.compareTo(new BigDecimal("0.90")) >= 0) {
                score += 25;
                flags.add("Claim is 90%+ of the sum insured");
            } else if (ratio.compareTo(new BigDecimal("0.60")) >= 0) {
                score += 12;
                flags.add("Claim is over 60% of the sum insured");
            }
        }
        if (s.daysSincePolicyStart() < 30) {
            score += 20;
            flags.add("Incident within 30 days of policy start");
        }
        if (s.daysSinceIncident() > 90) {
            score += 10;
            flags.add("Reported more than 90 days after the incident");
        }
        if (s.priorClaimsLast90Days() >= 2) {
            score += 20;
            flags.add(s.priorClaimsLast90Days() + " other claims by this customer in the last 90 days");
        } else if (s.priorClaimsLast90Days() == 1) {
            score += 8;
            flags.add("Another claim by this customer in the last 90 days");
        }
        if (s.duplicateDocument()) {
            score += 30;
            flags.add("A document was already used in a different claim");
        }
        if (s.documentCount() == 0) {
            score += 10;
            flags.add("No supporting documents");
        }
        if (s.billTotal() != null && s.billTotal().signum() > 0) {
            BigDecimal diff = s.claimedAmount().subtract(s.billTotal()).abs()
                    .divide(s.billTotal(), 4, RoundingMode.HALF_UP);
            if (s.claimedAmount().compareTo(s.billTotal()) > 0 && diff.compareTo(new BigDecimal("0.20")) > 0) {
                score += 15;
                flags.add("Claimed amount is more than 20% above the bill total");
            }
        }
        int ai = Math.max(0, Math.min(20, s.aiRiskAdjustment()));
        if (ai > 0) {
            score += ai;
            if (s.aiRiskReasons() != null) {
                s.aiRiskReasons().stream().limit(4).map(r -> "AI: " + r).forEach(flags::add);
            }
        }
        return new Result(Math.min(score, 100), flags);
    }
}
