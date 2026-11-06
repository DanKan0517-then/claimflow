package com.claimflow.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record UserDto(Long id, String email, String fullName, String phone, Role role, boolean active, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^[0-9+\\- ]{7,20}$", message = "Invalid phone number") String phone) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record AuthResponse(String token, UserDto user) {}

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 120) String fullName,
            @NotNull Role role,
            String phone) {}

    public record UpdateUserRequest(Boolean active, Role role) {}
}
