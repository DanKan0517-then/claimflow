package com.claimflow.security;

import com.claimflow.user.Role;

/** Lightweight principal built from the JWT, so requests don't hit the DB just to authenticate. */
public record AuthUser(Long id, String email, String name, Role role) {
    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean is(Role... roles) {
        for (Role r : roles) {
            if (r == role) return true;
        }
        return false;
    }
}
