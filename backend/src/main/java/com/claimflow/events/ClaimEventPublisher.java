package com.claimflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes claim events on a Redis channel. Every API replica subscribes and forwards them to its
 * own WebSocket clients, so a change made on replica 1 reaches a browser connected to replica 2.
 * Events go out only after the DB transaction commits, so clients never see uncommitted state.
 */
@Component
public class ClaimEventPublisher {

    public static final String CHANNEL = "claimflow:claim-events";
    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public ClaimEventPublisher(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    public void publish(ClaimEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(ClaimEvent event) {
        try {
            redis.convertAndSend(CHANNEL, mapper.writeValueAsString(event));
        } catch (Exception e) {
            // Live updates are best-effort; the data is already committed.
            log.warn("Could not publish claim event {}: {}", event.type(), e.getMessage());
        }
    }
}
