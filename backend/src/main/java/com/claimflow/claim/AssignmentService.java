package com.claimflow.claim;

import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Least-loaded auto assignment: the adjuster with the fewest open claims gets the next one. */
@Service
public class AssignmentService {

    private final UserRepository users;
    private final ClaimRepository claims;

    public AssignmentService(UserRepository users, ClaimRepository claims) {
        this.users = users;
        this.claims = claims;
    }

    public Long pickAdjuster() {
        List<User> adjusters = users.findByRoleAndActiveTrue(Role.ADJUSTER);
        if (adjusters.isEmpty()) return null;
        Map<Long, Long> load = new HashMap<>();
        for (Object[] row : claims.openLoadByAdjuster(ClaimStatus.openForInsurer())) {
            load.put((Long) row[0], (Long) row[1]);
        }
        return adjusters.stream()
                .min(Comparator.<User>comparingLong(u -> load.getOrDefault(u.getId(), 0L)).thenComparing(User::getId))
                .map(User::getId)
                .orElse(null);
    }
}
