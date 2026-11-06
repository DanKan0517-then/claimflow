package com.claimflow.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimIdOrderByUploadedAtAsc(Long claimId);

    long countByClaimId(Long claimId);

    /** Same file attached to a different claim is a classic fraud signal. */
    boolean existsBySha256AndClaimIdNot(String sha256, Long claimId);
}
