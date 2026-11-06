package com.claimflow.user;

import com.claimflow.common.ApiException;
import com.claimflow.security.JwtService;
import com.claimflow.user.UserDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public UserService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        User user = create(req.email(), req.password(), req.fullName(), Role.CUSTOMER, req.phone());
        return new AuthResponse(jwt.issue(user), UserDto.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Account is disabled");
        }
        return new AuthResponse(jwt.issue(user), UserDto.from(user));
    }

    @Transactional
    public User create(String email, String rawPassword, String fullName, Role role, String phone) {
        if (users.existsByEmailIgnoreCase(email.trim())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User(email, encoder.encode(rawPassword), fullName, role);
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone.trim());
        }
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
    }

    @Transactional(readOnly = true)
    public List<User> list(Role role) {
        return role == null ? users.findAllByOrderByCreatedAtDesc() : users.findByRoleOrderByFullNameAsc(role);
    }

    @Transactional
    public User update(Long id, UpdateUserRequest req, Long actorId) {
        User user = get(id);
        if (user.getId().equals(actorId)) {
            throw ApiException.badRequest("You can't change your own account here");
        }
        if (req.active() != null) user.setActive(req.active());
        if (req.role() != null) user.setRole(req.role());
        return user;
    }
}
