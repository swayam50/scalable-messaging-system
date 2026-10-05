package io.wulfcodes.messaging.chat.service.spec;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;

/**
 * Tracks the WebSocket sessions connected to THIS node, per user (a user can have several tabs).
 */
public interface SessionRegistry {

    void register(String userId, WebSocketSession session);

    void unregister(String userId, WebSocketSession session);

    Collection<WebSocketSession> sessionsOf(String userId);

    int connectionCount();
}
