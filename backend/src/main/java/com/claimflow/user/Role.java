package com.claimflow.user;

public enum Role {
    CUSTOMER, ADJUSTER, MANAGER, ADMIN;

    public boolean isStaff() {
        return this != CUSTOMER;
    }
}
