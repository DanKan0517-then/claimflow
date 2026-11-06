package com.claimflow.claim;

import com.claimflow.policy.PolicyType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimStatusTest {

    private Claim newClaim() {
        return new Claim("CLM-1", 1L, 1L, PolicyType.HEALTH, "Test claim", "Something happened to me today",
                LocalDate.now(), new BigDecimal("1000"));
    }

    @Test
    void happyPathTransitionsAreAllowed() {
        Claim c = newClaim();
        c.transitionTo(ClaimStatus.SUBMITTED);
        c.transitionTo(ClaimStatus.AI_REVIEW);
        c.transitionTo(ClaimStatus.UNDER_REVIEW);
        c.transitionTo(ClaimStatus.APPROVED);
        assertThat(c.getDecidedAt()).isNotNull();
        c.transitionTo(ClaimStatus.PAID);
        assertThat(c.getStatus()).isEqualTo(ClaimStatus.PAID);
        assertThat(c.getPaidAt()).isNotNull();
    }

    @Test
    void cannotSkipReview() {
        Claim c = newClaim();
        assertThatThrownBy(() -> c.transitionTo(ClaimStatus.APPROVED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void closedStatesAreTerminal() {
        assertThat(ClaimStatus.REJECTED.canTransitionTo(ClaimStatus.UNDER_REVIEW)).isFalse();
        assertThat(ClaimStatus.PAID.canTransitionTo(ClaimStatus.APPROVED)).isFalse();
        assertThat(ClaimStatus.APPROVED.canTransitionTo(ClaimStatus.REJECTED)).isFalse();
    }

    @Test
    void pendingApprovalOnlyGoesToDecision() {
        assertThat(ClaimStatus.PENDING_APPROVAL.canTransitionTo(ClaimStatus.APPROVED)).isTrue();
        assertThat(ClaimStatus.PENDING_APPROVAL.canTransitionTo(ClaimStatus.REJECTED)).isTrue();
        assertThat(ClaimStatus.PENDING_APPROVAL.canTransitionTo(ClaimStatus.INFO_REQUESTED)).isFalse();
    }

    @Test
    void draftIsNeverSlaBreached() {
        assertThat(newClaim().isSlaBreached()).isFalse();
    }
}
