package com.claimflow.ai;

import com.claimflow.claim.Claim;
import com.claimflow.claim.ClaimRepository;
import com.claimflow.claim.ClaimStatus;
import com.claimflow.config.AppProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Redis-backed work queue for AI analysis.
 *  - Producer: enqueues only AFTER the submitting transaction commits (no phantom jobs).
 *  - Consumers: every API replica polls; RPOP is atomic so each job goes to exactly one replica.
 *  - Retries: up to 3 attempts, then the job goes to a dead-letter list for inspection.
 *  - Recovery: a sweeper re-queues claims stuck in AI_REVIEW (e.g. a replica died mid-job).
 *    Combined with the idempotent consumer this gives at-least-once processing.
 */
@Component
public class AnalysisJobQueue {

    public static final String QUEUE = "claimflow:jobs:analysis";
    public static final String DEAD_LETTER = "claimflow:jobs:analysis:dead";
    private static final String ATTEMPTS = "claimflow:jobs:analysis:attempts";
    private static final int MAX_ATTEMPTS = 3;
    private static final Logger log = LoggerFactory.getLogger(AnalysisJobQueue.class);

    private final StringRedisTemplate redis;
    private final ClaimAnalysisService analysis;
    private final ClaimRepository claims;
    private final boolean workerEnabled;
    private final MeterRegistry metrics;

    public AnalysisJobQueue(StringRedisTemplate redis, ClaimAnalysisService analysis, ClaimRepository claims,
                            AppProperties props, MeterRegistry metrics) {
        this.redis = redis;
        this.analysis = analysis;
        this.claims = claims;
        this.workerEnabled = props.ai().workerEnabled();
        this.metrics = metrics;
        Gauge.builder("claimflow.queue.depth", this, AnalysisJobQueue::depth).tag("queue", "analysis").register(metrics);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisRequested(AnalysisRequested event) {
        enqueue(event.claimId());
    }

    public void enqueue(Long claimId) {
        try {
            redis.opsForList().leftPush(QUEUE, String.valueOf(claimId));
        } catch (Exception e) {
            // Redis down: the claim stays in AI_REVIEW and the sweeper will pick it up later.
            log.warn("Could not enqueue analysis for claim {}: {}", claimId, e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.ai.poll-interval-ms:1000}")
    public void poll() {
        if (!workerEnabled) return;
        for (int i = 0; i < 10; i++) {
            String raw;
            try {
                raw = redis.opsForList().rightPop(QUEUE);
            } catch (Exception e) {
                log.debug("Queue poll failed: {}", e.getMessage());
                return;
            }
            if (raw == null) return;
            process(Long.valueOf(raw));
        }
    }

    void process(Long claimId) {
        try {
            analysis.analyze(claimId);
            redis.opsForHash().delete(ATTEMPTS, claimId.toString());
            metrics.counter("claimflow.queue.jobs", "outcome", "ok").increment();
        } catch (Exception e) {
            long attempt = redis.opsForHash().increment(ATTEMPTS, claimId.toString(), 1);
            if (attempt < MAX_ATTEMPTS) {
                log.warn("Analysis of claim {} failed (attempt {}), retrying: {}", claimId, attempt, e.getMessage());
                redis.opsForList().leftPush(QUEUE, claimId.toString());
                metrics.counter("claimflow.queue.jobs", "outcome", "retry").increment();
            } else {
                log.error("Analysis of claim {} failed {} times, moving to dead-letter queue", claimId, attempt, e);
                redis.opsForList().leftPush(DEAD_LETTER, claimId.toString());
                redis.opsForHash().delete(ATTEMPTS, claimId.toString());
                metrics.counter("claimflow.queue.jobs", "outcome", "dead").increment();
            }
        }
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void recoverStuckClaims() {
        if (!workerEnabled) return;
        List<Claim> stuck = claims.findByStatusAndUpdatedAtBefore(ClaimStatus.AI_REVIEW,
                Instant.now().minus(3, ChronoUnit.MINUTES));
        for (Claim c : stuck) {
            log.info("Re-queuing claim {} stuck in AI_REVIEW", c.getClaimNumber());
            enqueue(c.getId());
        }
    }

    private double depth() {
        try {
            Long size = redis.opsForList().size(QUEUE);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return -1;
        }
    }
}
