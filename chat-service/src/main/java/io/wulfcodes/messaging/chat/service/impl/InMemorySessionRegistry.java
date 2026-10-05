package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    @Override
    public void register(String userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void unregister(String userId, WebSocketSession session) {
        // computeIfPresent is atomic per key, so we never drop a session registered concurrently
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    public Collection<WebSocketSession> sessionsOf(String userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public int connectionCount() {
        return sessionsByUser.values().stream().mapToInt(Set::size).sum();
    }
}
