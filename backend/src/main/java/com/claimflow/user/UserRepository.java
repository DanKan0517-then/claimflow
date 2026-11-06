package com.claimflow.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByRoleAndActiveTrue(Role role);

    List<User> findByRoleOrderByFullNameAsc(Role role);

    List<User> findAllByOrderByCreatedAtDesc();
}
