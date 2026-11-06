package com.claimflow.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Append-only trail of who did what to a claim. Joins the caller's transaction so it commits or rolls back with the change. */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long claimId, Long actorId, String action, Enum<?> from, Enum<?> to, String details) {
        repo.save(new AuditLog(claimId, actorId, action,
                from == null ? null : from.name(), to == null ? null : to.name(), details));
    }
}
