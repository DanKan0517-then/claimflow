package com.claimflow.claim;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Claim lifecycle as an explicit state machine.
 *
 * DRAFT -> SUBMITTED -> AI_REVIEW -> UNDER_REVIEW -> APPROVED -> PAID
 *                                         |  ^           ^
 *                                         v  |           |
 *                                   INFO_REQUESTED  PENDING_APPROVAL (big or risky claims)
 *                       UNDER_REVIEW / PENDING_APPROVAL -> REJECTED
 */
public enum ClaimStatus {
    DRAFT, SUBMITTED, AI_REVIEW, UNDER_REVIEW, INFO_REQUESTED, PENDING_APPROVAL, APPROVED, REJECTED, PAID;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(SUBMITTED),
            SUBMITTED, EnumSet.of(AI_REVIEW),
            AI_REVIEW, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(APPROVED, REJECTED, INFO_REQUESTED, PENDING_APPROVAL, AI_REVIEW),
            INFO_REQUESTED, EnumSet.of(UNDER_REVIEW, AI_REVIEW),
            PENDING_APPROVAL, EnumSet.of(APPROVED, REJECTED),
            APPROVED, EnumSet.of(PAID),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            PAID, EnumSet.noneOf(ClaimStatus.class)
    );

    public boolean canTransitionTo(ClaimStatus next) {
        return ALLOWED.get(this).contains(next);
    }

    /** No more work expected; SLA clock stops. */
    public boolean isClosed() {
        return this == APPROVED || this == REJECTED || this == PAID;
    }

    /** Waiting on the insurer (counts towards an adjuster's workload). */
    public static Set<ClaimStatus> openForInsurer() {
        return EnumSet.of(SUBMITTED, AI_REVIEW, UNDER_REVIEW, PENDING_APPROVAL);
    }
}
