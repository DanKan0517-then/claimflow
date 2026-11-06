package com.claimflow.user;

import com.claimflow.security.AuthUser;
import com.claimflow.user.UserDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<UserDto> list(@RequestParam(required = false) Role role) {
        return userService.list(role).stream().map(UserDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest req) {
        return UserDto.from(userService.create(req.email(), req.password(), req.fullName(), req.role(), req.phone()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id, @RequestBody UpdateUserRequest req, @AuthenticationPrincipal AuthUser actor) {
        return UserDto.from(userService.update(id, req, actor.id()));
    }
}
