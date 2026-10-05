package io.wulfcodes.messaging.chat.service.spec;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Set;

/**
 * Tracks the WebSocket sessions connected to THIS node, per user (a user can have several tabs).
 */
public interface SessionRegistry {

    /** @return true if this is the user's first session on this node (user just came online) */
    boolean register(String userId, WebSocketSession session);

    /** @return true if that was the user's last session on this node (user just went offline) */
    boolean unregister(String userId, WebSocketSession session);

    Collection<WebSocketSession> sessionsOf(String userId);

    boolean isConnected(String userId);

    Set<String> connectedUserIds();

    int connectionCount();
}
