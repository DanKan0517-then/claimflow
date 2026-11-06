package com.claimflow.claim;

import com.claimflow.audit.AuditLog;
import com.claimflow.claim.ClaimDtos.*;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds API DTOs. Looks up all referenced user names in one query to avoid N+1. */
@Component
public class ClaimMapper {

    private final UserRepository users;

    public ClaimMapper(UserRepository users) {
        this.users = users;
    }

    public Map<Long, User> usersById(Collection<Long> ids) {
        Set<Long> clean = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (clean.isEmpty()) return Map.of();
        return users.findAllById(clean).stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    public List<ClaimSummaryDto> summaries(List<Claim> list) {
        Map<Long, User> names = usersById(list.stream()
                .flatMap(c -> Stream.of(c.getCustomerId(), c.getAssignedAdjusterId())).toList());
        return list.stream().map(c -> summary(c, names)).toList();
    }

    public ClaimSummaryDto summary(Claim c, Map<Long, User> names) {
        return new ClaimSummaryDto(c.getId(), c.getClaimNumber(), c.getTitle(), c.getType(), c.getStatus(),
                c.getClaimedAmount(), c.getApprovedAmount(), c.getFraudScore(), ClaimDtos.riskLevel(c.getFraudScore()),
                c.getCustomerId(), name(names, c.getCustomerId()),
                c.getAssignedAdjusterId(), name(names, c.getAssignedAdjusterId()),
                c.getSlaDueAt(), c.isSlaBreached(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public DocumentDto document(ClaimDocument d) {
        return new DocumentDto(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(),
                d.getExtractedText() != null && !d.getExtractedText().isBlank(), d.getUploadedAt());
    }

    public CommentDto comment(ClaimComment c, Map<Long, User> names) {
        User u = names.get(c.getAuthorId());
        return new CommentDto(c.getId(), c.getAuthorId(), u == null ? "Unknown" : u.getFullName(),
                u == null ? null : u.getRole().name(), c.getBody(), c.isInternal(), c.getCreatedAt());
    }

    public AuditDto audit(AuditLog a, Map<Long, User> names) {
        String actor = a.getActorId() == null ? "System" : name(names, a.getActorId());
        return new AuditDto(a.getId(), a.getAction(), a.getFromStatus(), a.getToStatus(), a.getDetails(), actor, a.getCreatedAt());
    }

    private static String name(Map<Long, User> names, Long id) {
        if (id == null) return null;
        User u = names.get(id);
        return u == null ? null : u.getFullName();
    }
}
