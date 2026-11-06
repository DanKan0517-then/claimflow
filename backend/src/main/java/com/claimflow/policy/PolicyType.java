package com.claimflow.policy;

import java.time.Duration;

public enum PolicyType {
    HEALTH(Duration.ofHours(48)),
    MOTOR(Duration.ofHours(72)),
    PROPERTY(Duration.ofHours(120)),
    TRAVEL(Duration.ofHours(72));

    /** Target time from submission to a decision. */
    private final Duration sla;

    PolicyType(Duration sla) {
        this.sla = sla;
    }

    public Duration sla() {
        return sla;
    }
}
