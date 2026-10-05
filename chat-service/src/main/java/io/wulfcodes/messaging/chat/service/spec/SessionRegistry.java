package io.wulfcodes.messaging.chat.service.spec;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Set;

/**
 * Tracks the WebSocket sessions connected to THIS node, per user (a user can have several tabs).
 */
public interface SessionRegistry {

    void register(String userId, WebSocketSession session);

    void unregister(String userId, WebSocketSession session);

    Collection<WebSocketSession> sessionsOf(String userId);

    Set<String> connectedUserIds();

    int connectionCount();
}
