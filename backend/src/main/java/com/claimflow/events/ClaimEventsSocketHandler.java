package com.claimflow.events;

import com.claimflow.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds this replica's WebSocket connections and fans out events to the users allowed to see them. */
@Component
public class ClaimEventsSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventsSocketHandler.class);
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Decorator serialises concurrent sends and drops slow clients instead of blocking everyone.
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 5_000, 256 * 1024));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("ping".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    public void broadcast(ClaimEvent event, String json) {
        TextMessage msg = new TextMessage(json);
        sessions.values().forEach(s -> {
            AuthUser user = (AuthUser) s.getAttributes().get("user");
            boolean allowed = user != null && (user.isStaff() || user.id().equals(event.customerId()));
            if (allowed && s.isOpen()) {
                try {
                    s.sendMessage(msg);
                } catch (Exception e) {
                    log.debug("Dropping socket {}: {}", s.getId(), e.getMessage());
                    sessions.remove(s.getId());
                }
            }
        });
    }

    public int connectionCount() {
        return sessions.size();
    }
}
