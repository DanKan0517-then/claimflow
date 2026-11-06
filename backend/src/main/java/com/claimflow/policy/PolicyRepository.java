package com.claimflow.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    List<Policy> findByHolderIdOrderByCreatedAtDesc(Long holderId);

    List<Policy> findAllByOrderByCreatedAtDesc();

    @Query(value = "select nextval('policy_number_seq')", nativeQuery = true)
    long nextPolicyNumber();
}
