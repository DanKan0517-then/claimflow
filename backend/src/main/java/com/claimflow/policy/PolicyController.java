package com.claimflow.policy;

import com.claimflow.policy.PolicyDtos.*;
import com.claimflow.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public List<PolicyDto> list(@AuthenticationPrincipal AuthUser user) {
        return policyService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public PolicyDto issue(@Valid @RequestBody CreatePolicyRequest req) {
        return policyService.issue(req);
    }
}
