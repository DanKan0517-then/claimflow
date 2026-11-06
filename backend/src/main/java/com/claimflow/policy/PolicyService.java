package com.claimflow.policy;

import com.claimflow.common.ApiException;
import com.claimflow.policy.PolicyDtos.*;
import com.claimflow.security.AuthUser;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PolicyService {

    private final PolicyRepository policies;
    private final UserRepository users;

    public PolicyService(PolicyRepository policies, UserRepository users) {
        this.policies = policies;
        this.users = users;
    }

    @Transactional
    public PolicyDto issue(CreatePolicyRequest req) {
        if (!req.endDate().isAfter(req.startDate())) {
            throw ApiException.badRequest("End date must be after start date");
        }
        User holder = users.findById(req.holderId()).orElseThrow(() -> ApiException.notFound("Policy holder"));
        if (holder.getRole() != Role.CUSTOMER) {
            throw ApiException.badRequest("Policies can only be issued to customers");
        }
        String number = "POL-" + Year.now().getValue() + "-" + policies.nextPolicyNumber();
        Policy p = policies.save(new Policy(number, holder.getId(), req.type(), req.sumInsured(), req.premium(),
                req.startDate(), req.endDate()));
        return PolicyDto.from(p, holder.getFullName());
    }

    @Transactional(readOnly = true)
    public List<PolicyDto> list(AuthUser user) {
        List<Policy> list = user.isStaff()
                ? policies.findAllByOrderByCreatedAtDesc()
                : policies.findByHolderIdOrderByCreatedAtDesc(user.id());
        Map<Long, String> names = users.findAllById(list.stream().map(Policy::getHolderId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return list.stream().map(p -> PolicyDto.from(p, names.getOrDefault(p.getHolderId(), "?"))).toList();
    }

    @Transactional(readOnly = true)
    public Policy getForUser(Long id, AuthUser user) {
        Policy p = policies.findById(id).orElseThrow(() -> ApiException.notFound("Policy"));
        if (!user.isStaff() && !p.getHolderId().equals(user.id())) {
            throw ApiException.notFound("Policy"); // don't leak that it exists
        }
        return p;
    }
}
