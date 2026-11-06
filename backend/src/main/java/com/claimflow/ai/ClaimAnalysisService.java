package com.claimflow.ai;

import com.claimflow.audit.AuditService;
import com.claimflow.claim.*;
import com.claimflow.events.ClaimEvent;
import com.claimflow.events.ClaimEventPublisher;
import com.claimflow.notification.NotificationService;
import com.claimflow.policy.Policy;
import com.claimflow.policy.PolicyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The AI step of the pipeline. Three phases so the slow LLM call never holds a DB transaction open:
 *   1. read claim + policy + documents (short read-only tx)
 *   2. call the LLM / rules (no tx)
 *   3. write results and move AI_REVIEW -> UNDER_REVIEW (short tx, optimistic lock)
 * It is idempotent: if the claim is no longer in AI_REVIEW the job is a no-op, so duplicate jobs are harmless.
 */
@Service
public class ClaimAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ClaimAnalysisService.class);

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final PolicyRepository policies;
    private final AiRouter ai;
    private final FraudScorer scorer;
    private final AssignmentService assignment;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ClaimEventPublisher events;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Timer timer;

    public ClaimAnalysisService(ClaimRepository claims, ClaimDocumentRepository documents, PolicyRepository policies,
                                AiRouter ai, FraudScorer scorer, AssignmentService assignment, AuditService audit,
                                NotificationService notifications, ClaimEventPublisher events, ObjectMapper json,
                                PlatformTransactionManager txManager, MeterRegistry metrics) {
        this.claims = claims;
        this.documents = documents;
        this.policies = policies;
        this.ai = ai;
        this.scorer = scorer;
        this.assignment = assignment;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
        this.json = json;
        this.tx = new TransactionTemplate(txManager);
        this.timer = Timer.builder("claimflow.ai.analysis").description("End-to-end AI analysis time")
                .publishPercentiles(0.5, 0.95).register(metrics);
    }

    private record Snapshot(Claim claim, Policy policy, ClaimContext context, FraudScorer.Signals baseSignals) {}

    public void analyze(Long claimId) {
        timer.record(() -> doAnalyze(claimId));
    }

    private void doAnalyze(Long claimId) {
        Snapshot snap = tx.execute(status -> load(claimId));
        if (snap == null) return; // nothing to do

        AiAnalysis analysis = ai.analyze(snap.context());
        FraudScorer.Signals s = snap.baseSignals();
        FraudScorer.Result fraud = scorer.score(new FraudScorer.Signals(s.claimedAmount(), s.sumInsured(),
                s.daysSincePolicyStart(), s.daysSinceIncident(), s.priorClaimsLast90Days(), s.duplicateDocument(),
                s.documentCount(), analysis.billTotal(), analysis.riskAdjustment(), analysis.riskReasons()));

        tx.executeWithoutResult(status -> save(claimId, snap.claim().getVersion(), analysis, fraud));
    }

    private Snapshot load(Long claimId) {
        Claim claim = claims.findById(claimId).orElse(null);
        if (claim == null || claim.getStatus() != ClaimStatus.AI_REVIEW) {
            log.debug("Skipping analysis for claim {} (not in AI_REVIEW)", claimId);
            return null;
        }
        Policy policy = policies.findById(claim.getPolicyId()).orElseThrow();
        List<ClaimDocument> docs = documents.findByClaimIdOrderByUploadedAtAsc(claimId);
        String docText = docs.stream()
                .filter(d -> d.getExtractedText() != null && !d.getExtractedText().isBlank())
                .map(d -> "--- " + d.getFileName() + " ---\n" + d.getExtractedText())
                .collect(Collectors.joining("\n\n"));
        boolean duplicate = docs.stream().anyMatch(d -> documents.existsBySha256AndClaimIdNot(d.getSha256(), claimId));
        long prior = claims.countRecentClaims(claim.getCustomerId(), claimId, Instant.now().minus(90, ChronoUnit.DAYS));

        ClaimContext ctx = new ClaimContext(claim.getType().name(), claim.getTitle(), claim.getDescription(),
                claim.getClaimedAmount(), policy.getSumInsured(), claim.getIncidentDate(), policy.getStartDate(),
                docText.isBlank() ? null : docText);
        FraudScorer.Signals signals = new FraudScorer.Signals(claim.getClaimedAmount(), policy.getSumInsured(),
                ChronoUnit.DAYS.between(policy.getStartDate(), claim.getIncidentDate()),
                ChronoUnit.DAYS.between(claim.getIncidentDate(), LocalDate.now()),
                prior, duplicate, docs.size(), null, 0, List.of());
        return new Snapshot(claim, policy, ctx, signals);
    }

    private void save(Long claimId, long expectedVersion, AiAnalysis analysis, FraudScorer.Result fraud) {
        Claim claim = claims.findById(claimId).orElseThrow();
        if (claim.getStatus() != ClaimStatus.AI_REVIEW || claim.getVersion() != expectedVersion) {
            log.info("Claim {} changed while being analysed; dropping stale result", claimId);
            return;
        }
        claim.setFraudScore(fraud.score());
        claim.setFraudFlags(String.join("\n", fraud.flags()));
        claim.setAiSummary(analysis.summary());
        claim.setAiProvider(analysis.provider());
        try {
            claim.setAiExtracted(json.writeValueAsString(analysis.extracted()));
        } catch (Exception e) {
            claim.setAiExtracted(null);
        }
        if (claim.getAssignedAdjusterId() == null) {
            claim.setAssignedAdjusterId(assignment.pickAdjuster());
        }
        ClaimStatus from = claim.transitionTo(ClaimStatus.UNDER_REVIEW);
        audit.record(claimId, null, "AI_ANALYZED", from, ClaimStatus.UNDER_REVIEW,
                "Fraud score " + fraud.score() + " (" + fraud.riskLevel() + ") via " + analysis.provider());
        notifications.notify(claim.getAssignedAdjusterId(), claimId,
                claim.getClaimNumber() + " is ready for review (risk: " + fraud.riskLevel() + ")");
        events.publish(ClaimEvent.of("CLAIM_ANALYZED", claim, "AI review done for " + claim.getClaimNumber()));
    }
}
