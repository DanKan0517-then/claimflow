package com.claimflow.claim;

import com.claimflow.common.ApiException;
import com.claimflow.security.AuthUser;
import org.springframework.stereotype.Component;

/** Single place that decides whether a user may see a claim. Customers only ever see their own. */
@Component
public class ClaimAccess {

    private final ClaimRepository claims;

    public ClaimAccess(ClaimRepository claims) {
        this.claims = claims;
    }

    public Claim loadForUser(Long claimId, AuthUser user) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> ApiException.notFound("Claim"));
        if (!user.isStaff() && !claim.getCustomerId().equals(user.id())) {
            throw ApiException.notFound("Claim"); // 404 not 403: don't reveal other customers' claim ids
        }
        return claim;
    }
}
