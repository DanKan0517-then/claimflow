package com.claimflow.claim;

import com.claimflow.ai.AnalysisRequested;
import com.claimflow.audit.AuditLog;
import com.claimflow.audit.AuditLogRepository;
import com.claimflow.audit.AuditService;
import com.claimflow.claim.ClaimDtos.*;
import com.claimflow.common.ApiException;
import com.claimflow.common.PageResponse;
import com.claimflow.events.ClaimEvent;
import com.claimflow.events.ClaimEventPublisher;
import com.claimflow.notification.NotificationService;
import com.claimflow.policy.Policy;
import com.claimflow.policy.PolicyService;
import com.claimflow.policy.PolicyType;
import com.claimflow.security.AuthUser;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.*;
import java.util.stream.Stream;

@Service
public class ClaimService {

    private static final Set<String> CUSTOMER_VISIBLE_ACTIONS = Set.of(
            "CREATED", "SUBMITTED", "INFO_REQUESTED", "CUSTOMER_RESPONDED", "APPROVED", "REJECTED", "PAID",
            "SENT_FOR_MANAGER_APPROVAL", "AI_ANALYZED", "DOCUMENT_UPLOADED");

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final ClaimCommentRepository comments;
    private final AuditLogRepository auditLogs;
    private final ClaimAccess access;
    private final ClaimMapper mapper;
    private final PolicyService policies;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClaimEventPublisher events;
    private final ApplicationEventPublisher appEvents;
    private final ClaimWorkflowService workflow;
    private final ObjectMapper json;
    private final MeterRegistry metrics;

    public ClaimService(ClaimRepository claims, ClaimDocumentRepository documents, ClaimCommentRepository comments,
                        AuditLogRepository auditLogs, ClaimAccess access, ClaimMapper mapper, PolicyService policies,
                        AuditService audit, NotificationService notifications, ClaimEventPublisher events,
                        ApplicationEventPublisher appEvents, ClaimWorkflowService workflow, ObjectMapper json,
                        MeterRegistry metrics) {
        this.claims = claims;
        this.documents = documents;
        this.comments = comments;
        this.auditLogs = auditLogs;
        this.access = access;
        this.mapper = mapper;
        this.policies = policies;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
        this.appEvents = appEvents;
        this.workflow = workflow;
        this.json = json;
        this.metrics = metrics;
    }

    // ---------------------------------------------------------------- customer actions

    @Transactional
    public ClaimDetailDto create(AuthUser user, CreateClaimRequest req) {
        if (user.role() != Role.CUSTOMER) {
            throw ApiException.forbidden("Only policy holders can file claims");
        }
        Policy policy = policies.getForUser(req.policyId(), user);
        if (!policy.covers(req.incidentDate())) {
            throw ApiException.badRequest("The incident date is outside the policy period or the policy is not active");
        }
        if (req.claimedAmount().compareTo(policy.getSumInsured()) > 0) {
            throw ApiException.badRequest("Claimed amount can't be more than the sum insured");
        }
        String number = "CLM-" + Year.now().getValue() + "-" + claims.nextClaimNumber();
        Claim claim = claims.save(new Claim(number, policy.getId(), user.id(), policy.getType(),
                req.title().trim(), req.description().trim(), req.incidentDate(), req.claimedAmount()));
        audit.record(claim.getId(), user.id(), "CREATED", null, ClaimStatus.DRAFT, null);
        return detail(user, claim.getId());
    }

    /** Draft -> Submitted -> AI review. The AI job is queued only once this transaction commits. */
    @Transactional
    public ClaimDetailDto submit(AuthUser user, Long id) {
        Claim claim = access.loadForUser(id, user);
        if (!claim.getCustomerId().equals(user.id())) {
            throw ApiException.forbidden("Only the policy holder can submit this claim");
        }
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw ApiException.badRequest("Claim was already submitted");
        }
        claim.transitionTo(ClaimStatus.SUBMITTED);
        claim.setSlaDueAt(Instant.now().plus(claim.getType().sla()));
        audit.record(id, user.id(), "SUBMITTED", ClaimStatus.DRAFT, ClaimStatus.SUBMITTED, null);
        claim.transitionTo(ClaimStatus.AI_REVIEW);
        audit.record(id, null, "AI_REVIEW_STARTED", ClaimStatus.SUBMITTED, ClaimStatus.AI_REVIEW, null);
        appEvents.publishEvent(new AnalysisRequested(id));
        events.publish(ClaimEvent.of("CLAIM_SUBMITTED", claim, "New claim " + claim.getClaimNumber()));
        metrics.counter("claimflow.claims.submitted", "type", claim.getType().name()).increment();
        return detail(user, id);
    }

    /** Customer answers an info request; the claim goes back through AI (new documents may have been added). */
    @Transactional
    public ClaimDetailDto respond(AuthUser user, Long id, RespondRequest req) {
        Claim claim = access.loadForUser(id, user);
        if (!claim.getCustomerId().equals(user.id())) {
            throw ApiException.forbidden("Only the policy holder can respond");
        }
        if (claim.getStatus() != ClaimStatus.INFO_REQUESTED) {
            throw ApiException.badRequest("No information was requested on this claim");
        }
        comments.save(new ClaimComment(id, user.id(), req.message().trim(), false));
        ClaimStatus from = claim.transitionTo(ClaimStatus.AI_REVIEW);
        audit.record(id, user.id(), "CUSTOMER_RESPONDED", from, ClaimStatus.AI_REVIEW, null);
        notifications.notify(claim.getAssignedAdjusterId(), id, claim.getClaimNumber() + ": customer responded to your request");
        appEvents.publishEvent(new AnalysisRequested(id));
        events.publish(ClaimEvent.of("CLAIM_UPDATED", claim, "Customer responded"));
        return detail(user, id);
    }

    @Transactional
    public CommentDto comment(AuthUser user, Long id, CommentRequest req) {
        Claim claim = access.loadForUser(id, user);
        boolean internal = req.internal() && user.isStaff();
        ClaimComment saved = comments.save(new ClaimComment(id, user.id(), req.body().trim(), internal));
        if (!user.isStaff()) {
            notifications.notify(claim.getAssignedAdjusterId(), id, claim.getClaimNumber() + ": new message from customer");
        } else if (!internal) {
            notifications.notify(claim.getCustomerId(), id, claim.getClaimNumber() + ": new message from your claims team");
        }
        events.publish(ClaimEvent.of("CLAIM_COMMENT", claim, null));
        Map<Long, User> names = mapper.usersById(List.of(user.id()));
        return mapper.comment(saved, names);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public PageResponse<ClaimSummaryDto> list(AuthUser user, ClaimStatus status, PolicyType type, String scope,
                                              String q, Integer minFraud, Pageable pageable) {
        Specification<Claim> spec;
        if (user.isStaff()) {
            spec = ClaimSpecifications.notDraft();
            if ("mine".equalsIgnoreCase(scope)) spec = spec.and(ClaimSpecifications.assignedTo(user.id()));
            else if ("unassigned".equalsIgnoreCase(scope)) spec = spec.and(ClaimSpecifications.unassigned());
            if (minFraud != null) spec = spec.and(ClaimSpecifications.minFraud(minFraud));
        } else {
            spec = ClaimSpecifications.customer(user.id());
        }
        if (status != null) spec = spec.and(ClaimSpecifications.status(status));
        if (type != null) spec = spec.and(ClaimSpecifications.type(type));
        if (q != null && !q.isBlank()) spec = spec.and(ClaimSpecifications.search(q));
        Page<Claim> page = claims.findAll(spec, pageable);
        return PageResponse.of(page, mapper.summaries(page.getContent()));
    }

    @Transactional(readOnly = true)
    public ClaimDetailDto detail(AuthUser user, Long id) {
        Claim c = access.loadForUser(id, user);
        Policy policy = policies.getForUser(c.getPolicyId(), staffView(user, c));
        List<ClaimDocument> docs = documents.findByClaimIdOrderByUploadedAtAsc(id);
        List<ClaimComment> commentList = comments.findByClaimIdOrderByCreatedAtAsc(id).stream()
                .filter(cm -> user.isStaff() || !cm.isInternal()).toList();
        List<AuditLog> logs = auditLogs.findByClaimIdOrderByCreatedAtAscIdAsc(id).stream()
                .filter(a -> user.isStaff() || CUSTOMER_VISIBLE_ACTIONS.contains(a.getAction())).toList();

        Map<Long, User> names = mapper.usersById(Stream.of(
                Stream.of(c.getCustomerId(), c.getAssignedAdjusterId()),
                commentList.stream().map(ClaimComment::getAuthorId),
                logs.stream().map(AuditLog::getActorId)).flatMap(s -> s).toList());

        boolean staff = user.isStaff();
        return new ClaimDetailDto(
                mapper.summary(c, names),
                c.getDescription(),
                c.getIncidentDate(),
                new PolicyBrief(policy.getId(), policy.getPolicyNumber(), policy.getType(), policy.getSumInsured(),
                        policy.getStartDate(), policy.getEndDate()),
                c.getDecisionReason(),
                c.getDecidedAt(),
                c.getPaidAt(),
                staff ? c.getAiSummary() : null,
                staff ? parseExtracted(c.getAiExtracted()) : null,
                staff ? c.getAiProvider() : null,
                staff ? splitFlags(c.getFraudFlags()) : null,
                docs.stream().map(mapper::document).toList(),
                commentList.stream().map(cm -> mapper.comment(cm, names)).toList(),
                logs.stream().map(a -> {
                    var dto = mapper.audit(a, names);
                    return staff ? dto : new AuditDto(dto.id(), dto.action(), dto.fromStatus(), dto.toStatus(),
                            null, null, dto.createdAt());
                }).toList(),
                workflow.allowedActions(c, user),
                c.getVersion());
    }

    private static AuthUser staffView(AuthUser user, Claim c) {
        // the claim access check already passed; read the policy with the claim owner's rights
        return user.isStaff() ? user : new AuthUser(c.getCustomerId(), user.email(), user.name(), user.role());
    }

    private Map<String, Object> parseExtracted(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return json.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("raw", raw);
        }
    }

    private static List<String> splitFlags(String flags) {
        if (flags == null || flags.isBlank()) return List.of();
        return Arrays.stream(flags.split("\n")).filter(s -> !s.isBlank()).toList();
    }
}
