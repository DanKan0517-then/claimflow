package com.claimflow.claim;

import com.claimflow.policy.PolicyType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Composable filters for the claims list. */
public final class ClaimSpecifications {
    private ClaimSpecifications() {}

    public static Specification<Claim> customer(Long customerId) {
        return (root, q, cb) -> cb.equal(root.get("customerId"), customerId);
    }

    public static Specification<Claim> notDraft() {
        return (root, q, cb) -> cb.notEqual(root.get("status"), ClaimStatus.DRAFT);
    }

    public static Specification<Claim> status(ClaimStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Claim> type(PolicyType type) {
        return (root, q, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Claim> assignedTo(Long adjusterId) {
        return (root, q, cb) -> cb.equal(root.get("assignedAdjusterId"), adjusterId);
    }

    public static Specification<Claim> unassigned() {
        return (root, q, cb) -> cb.isNull(root.get("assignedAdjusterId"));
    }

    public static Specification<Claim> minFraud(int score) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("fraudScore"), score);
    }

    public static Specification<Claim> search(String text) {
        String like = "%" + text.toLowerCase(Locale.ROOT).trim() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("claimNumber")), like),
                cb.like(cb.lower(root.get("title")), like));
    }
}
