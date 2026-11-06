package com.claimflow.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RedisEventSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisEventSubscriber.class);

    private final RedisMessageListenerContainer container;
    private final ClaimEventsSocketHandler sockets;
    private final ObjectMapper mapper;

    public RedisEventSubscriber(RedisMessageListenerContainer container, ClaimEventsSocketHandler sockets,
                                ObjectMapper mapper) {
        this.container = container;
        this.sockets = sockets;
        this.mapper = mapper;
    }

    @PostConstruct
    void subscribe() {
        container.addMessageListener(this, new ChannelTopic(ClaimEventPublisher.CHANNEL));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            ClaimEvent event = mapper.readValue(json, ClaimEvent.class);
            sockets.broadcast(event, json);
        } catch (Exception e) {
            log.warn("Bad claim event payload: {}", e.getMessage());
        }
    }
}
