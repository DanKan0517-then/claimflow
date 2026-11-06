package com.claimflow.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long>, JpaSpecificationExecutor<Claim> {

    @Query(value = "select nextval('claim_number_seq')", nativeQuery = true)
    long nextClaimNumber();

    @Query("select count(c) from Claim c where c.customerId = :customerId and c.id <> :excludeId " +
            "and c.status <> com.claimflow.claim.ClaimStatus.DRAFT and c.createdAt >= :since")
    long countRecentClaims(@Param("customerId") Long customerId, @Param("excludeId") Long excludeId,
                           @Param("since") Instant since);

    /** [adjusterId, openCount] used for least-loaded auto assignment. */
    @Query("select c.assignedAdjusterId, count(c) from Claim c where c.assignedAdjusterId is not null " +
            "and c.status in :statuses group by c.assignedAdjusterId")
    List<Object[]> openLoadByAdjuster(@Param("statuses") Collection<ClaimStatus> statuses);

    List<Claim> findByStatusAndUpdatedAtBefore(ClaimStatus status, Instant before);

    long countByStatus(ClaimStatus status);
}
