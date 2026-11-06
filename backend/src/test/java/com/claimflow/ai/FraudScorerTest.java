package com.claimflow.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FraudScorerTest {

    private final FraudScorer scorer = new FraudScorer();

    private FraudScorer.Signals clean() {
        return new FraudScorer.Signals(new BigDecimal("20000"), new BigDecimal("500000"), 200, 3, 0,
                false, 2, new BigDecimal("20000"), 0, List.of());
    }

    @Test
    void cleanClaimScoresLow() {
        FraudScorer.Result r = scorer.score(clean());
        assertThat(r.score()).isZero();
        assertThat(r.flags()).isEmpty();
        assertThat(r.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void suspiciousClaimScoresHighWithReasons() {
        FraudScorer.Signals s = new FraudScorer.Signals(new BigDecimal("480000"), new BigDecimal("500000"), 10, 2, 2,
                true, 1, new BigDecimal("300000"), 12, List.of("Story doesn't match the bill"));
        FraudScorer.Result r = scorer.score(s);
        assertThat(r.score()).isEqualTo(100); // capped
        assertThat(r.riskLevel()).isEqualTo("HIGH");
        assertThat(r.flags()).anyMatch(f -> f.contains("90%"))
                .anyMatch(f -> f.contains("30 days"))
                .anyMatch(f -> f.contains("different claim"))
                .anyMatch(f -> f.contains("20% above"))
                .anyMatch(f -> f.startsWith("AI:"));
    }

    @Test
    void noDocumentsAndLateReportingAddUp() {
        FraudScorer.Signals s = new FraudScorer.Signals(new BigDecimal("1000"), new BigDecimal("500000"), 400, 120, 0,
                false, 0, null, 0, List.of());
        assertThat(scorer.score(s).score()).isEqualTo(20);
    }

    @Test
    void aiAdjustmentIsClamped() {
        FraudScorer.Signals base = clean();
        FraudScorer.Signals s = new FraudScorer.Signals(base.claimedAmount(), base.sumInsured(), base.daysSincePolicyStart(),
                base.daysSinceIncident(), 0, false, 2, null, 999, List.of("x"));
        assertThat(scorer.score(s).score()).isEqualTo(20);
    }
}
