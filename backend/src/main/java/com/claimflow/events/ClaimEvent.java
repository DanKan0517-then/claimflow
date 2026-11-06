package com.claimflow.events;

import java.time.Instant;

/** Small, non-sensitive event pushed to browsers over WebSocket. */
public record ClaimEvent(
        String type,
        Long claimId,
        String claimNumber,
        String status,
        Long customerId,
        Long assignedAdjusterId,
        String message,
        Instant at
) {
    public static ClaimEvent of(String type, com.claimflow.claim.Claim c, String message) {
        return new ClaimEvent(type, c.getId(), c.getClaimNumber(), c.getStatus().name(), c.getCustomerId(),
                c.getAssignedAdjusterId(), message, Instant.now());
    }
}
