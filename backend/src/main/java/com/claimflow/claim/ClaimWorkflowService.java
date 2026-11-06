package com.claimflow.claim;

import com.claimflow.ai.AnalysisRequested;
import com.claimflow.audit.AuditService;
import com.claimflow.claim.ClaimDtos.ActionRequest;
import com.claimflow.common.ApiException;
import com.claimflow.common.Money;
import com.claimflow.config.AppProperties;
import com.claimflow.events.ClaimEvent;
import com.claimflow.events.ClaimEventPublisher;
import com.claimflow.notification.NotificationService;
import com.claimflow.policy.Policy;
import com.claimflow.policy.PolicyRepository;
import com.claimflow.security.AuthUser;
import com.claimflow.user.Role;
import com.claimflow.user.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Business rules for what staff can do to a claim and when.
 *  - Adjusters work claims assigned to them (or pick up unassigned ones).
 *  - An adjuster can approve on their own only up to the threshold and only for low/medium risk claims;
 *    anything bigger or high risk goes to a manager (maker-checker / four-eyes principle).
 *  - Only managers/admins can approve pending approvals and mark claims paid.
 */
@Service
public class ClaimWorkflowService {

    private final ClaimAccess access;
    private final PolicyRepository policies;
    private final UserRepository users;
    private final ClaimCommentRepository comments;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClaimEventPublisher events;
    private final ApplicationEventPublisher appEvents;
    private final AppProperties.Workflow rules;
    private final MeterRegistry metrics;

    public ClaimWorkflowService(ClaimAccess access, PolicyRepository policies, UserRepository users,
                                ClaimCommentRepository comments, AuditService audit, NotificationService notifications,
                                ClaimEventPublisher events, ApplicationEventPublisher appEvents, AppProperties props,
                                MeterRegistry metrics) {
        this.access = access;
        this.policies = policies;
        this.users = users;
        this.comments = comments;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
        this.appEvents = appEvents;
        this.rules = props.workflow();
        this.metrics = metrics;
    }

    @Transactional
    public void act(AuthUser user, Long claimId, ActionRequest req) {
        if (!user.isStaff()) throw ApiException.forbidden("Only claims staff can do this");
        Claim claim = access.loadForUser(claimId, user);
        if (req.expectedVersion() != null && req.expectedVersion() != claim.getVersion()) {
            throw ApiException.conflict("This claim was updated by someone else. Refresh and try again.");
        }
        if (req.action() == ClaimAction.ASSIGN_TO_ME && user.id().equals(claim.getAssignedAdjusterId())
                && !claim.getStatus().isClosed()) {
            return; // already mine: idempotent no-op
        }
        if (!allowedActions(claim, user).contains(req.action())) {
            throw ApiException.badRequest("You can't " + req.action().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    + " a claim that is " + claim.getStatus());
        }

        switch (req.action()) {
            case ASSIGN_TO_ME -> assignToMe(user, claim);
            case APPROVE -> approve(user, claim, req);
            case REJECT -> reject(user, claim, req);
            case REQUEST_INFO -> requestInfo(user, claim, req);
            case MARK_PAID -> markPaid(user, claim);
            case REANALYZE -> reanalyze(user, claim);
        }
        metrics.counter("claimflow.claims.actions", "action", req.action().name(),
                "status", claim.getStatus().name()).increment();
        events.publish(ClaimEvent.of("CLAIM_UPDATED", claim, null));
    }

    /** What the current user is allowed to do right now; the UI shows exactly these buttons. */
    public List<ClaimAction> allowedActions(Claim c, AuthUser user) {
        List<ClaimAction> out = new ArrayList<>();
        if (!user.isStaff()) return out;
        boolean manager = user.is(Role.MANAGER, Role.ADMIN);
        boolean mine = user.id().equals(c.getAssignedAdjusterId());
        boolean canWork = manager || mine || c.getAssignedAdjusterId() == null;

        if (!c.getStatus().isClosed() && c.getStatus() != ClaimStatus.DRAFT && !mine) {
            out.add(ClaimAction.ASSIGN_TO_ME);
        }
        switch (c.getStatus()) {
            case UNDER_REVIEW -> {
                if (canWork) {
                    out.add(ClaimAction.APPROVE);
                    out.add(ClaimAction.REJECT);
                    out.add(ClaimAction.REQUEST_INFO);
                    out.add(ClaimAction.REANALYZE);
                }
            }
            case PENDING_APPROVAL -> {
                if (manager) {
                    out.add(ClaimAction.APPROVE);
                    out.add(ClaimAction.REJECT);
                }
            }
            case APPROVED -> {
                if (manager) out.add(ClaimAction.MARK_PAID);
            }
            default -> { }
        }
        return out;
    }

    private void assignToMe(AuthUser user, Claim claim) {
        Long previous = claim.getAssignedAdjusterId();
        claim.setAssignedAdjusterId(user.id());
        audit.record(claim.getId(), user.id(), "ASSIGNED", null, null,
                previous == null ? "Picked up by " + user.name() : "Reassigned to " + user.name());
    }

    private void approve(AuthUser user, Claim claim, ActionRequest req) {
        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow(() -> ApiException.notFound("Policy"));
        BigDecimal amount = req.approvedAmount() != null ? req.approvedAmount()
                : claim.getApprovedAmount() != null ? claim.getApprovedAmount() : claim.getClaimedAmount();
        if (amount.compareTo(claim.getClaimedAmount()) > 0) {
            throw ApiException.badRequest("Approved amount can't be more than the claimed amount");
        }
        if (amount.compareTo(policy.getSumInsured()) > 0) {
            throw ApiException.badRequest("Approved amount can't be more than the sum insured");
        }
        if (claim.getAssignedAdjusterId() == null) claim.setAssignedAdjusterId(user.id());
        claim.setApprovedAmount(amount);
        claim.setDecisionReason(blankToNull(req.reason()));

        boolean manager = user.is(Role.MANAGER, Role.ADMIN);
        boolean bigClaim = amount.compareTo(rules.managerApprovalThreshold()) > 0;
        boolean risky = claim.getFraudScore() != null && claim.getFraudScore() >= rules.highRiskFraudScore();

        if (claim.getStatus() == ClaimStatus.UNDER_REVIEW && !manager && (bigClaim || risky)) {
            ClaimStatus from = claim.transitionTo(ClaimStatus.PENDING_APPROVAL);
            String why = bigClaim ? "amount above " + rupees(rules.managerApprovalThreshold()) : "high fraud risk";
            audit.record(claim.getId(), user.id(), "SENT_FOR_MANAGER_APPROVAL", from, ClaimStatus.PENDING_APPROVAL,
                    "Recommended " + rupees(amount) + " (" + why + ")");
            users.findByRoleAndActiveTrue(Role.MANAGER).forEach(m -> notifications.notify(m.getId(), claim.getId(),
                    claim.getClaimNumber() + " needs your approval (" + rupees(amount) + ")"));
            notifications.notify(claim.getCustomerId(), claim.getId(),
                    claim.getClaimNumber() + " has been reviewed and is awaiting final approval");
            return;
        }
        ClaimStatus from = claim.transitionTo(ClaimStatus.APPROVED);
        audit.record(claim.getId(), user.id(), "APPROVED", from, ClaimStatus.APPROVED, "Approved " + rupees(amount));
        notifications.notify(claim.getCustomerId(), claim.getId(),
                "Good news: " + claim.getClaimNumber() + " was approved for " + rupees(amount));
    }

    private void reject(AuthUser user, Claim claim, ActionRequest req) {
        String reason = blankToNull(req.reason());
        if (reason == null || reason.length() < 10) {
            throw ApiException.badRequest("Give the customer a reason (at least 10 characters)");
        }
        if (claim.getAssignedAdjusterId() == null) claim.setAssignedAdjusterId(user.id());
        claim.setDecisionReason(reason);
        claim.setApprovedAmount(null);
        ClaimStatus from = claim.transitionTo(ClaimStatus.REJECTED);
        audit.record(claim.getId(), user.id(), "REJECTED", from, ClaimStatus.REJECTED, reason);
        notifications.notify(claim.getCustomerId(), claim.getId(), claim.getClaimNumber() + " was rejected: " + reason);
    }

    private void requestInfo(AuthUser user, Claim claim, ActionRequest req) {
        String reason = blankToNull(req.reason());
        if (reason == null) throw ApiException.badRequest("Say what information you need");
        if (claim.getAssignedAdjusterId() == null) claim.setAssignedAdjusterId(user.id());
        comments.save(new ClaimComment(claim.getId(), user.id(), reason, false));
        ClaimStatus from = claim.transitionTo(ClaimStatus.INFO_REQUESTED);
        audit.record(claim.getId(), user.id(), "INFO_REQUESTED", from, ClaimStatus.INFO_REQUESTED, reason);
        notifications.notify(claim.getCustomerId(), claim.getId(),
                claim.getClaimNumber() + ": we need more information - " + reason);
    }

    private void markPaid(AuthUser user, Claim claim) {
        ClaimStatus from = claim.transitionTo(ClaimStatus.PAID);
        audit.record(claim.getId(), user.id(), "PAID", from, ClaimStatus.PAID,
                "Paid " + rupees(claim.getApprovedAmount()));
        notifications.notify(claim.getCustomerId(), claim.getId(),
                claim.getClaimNumber() + ": " + rupees(claim.getApprovedAmount()) + " has been paid out");
    }

    private void reanalyze(AuthUser user, Claim claim) {
        ClaimStatus from = claim.transitionTo(ClaimStatus.AI_REVIEW);
        audit.record(claim.getId(), user.id(), "REANALYZE_REQUESTED", from, ClaimStatus.AI_REVIEW, null);
        appEvents.publishEvent(new AnalysisRequested(claim.getId()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String rupees(BigDecimal amount) {
        return Money.inr(amount);
    }
}
