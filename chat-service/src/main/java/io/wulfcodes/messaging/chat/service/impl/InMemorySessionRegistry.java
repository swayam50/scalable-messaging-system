package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    /** compute() runs atomically per key, so "first session" is decided exactly once. */
    @Override
    public boolean register(String userId, WebSocketSession session) {
        AtomicBoolean first = new AtomicBoolean();
        sessionsByUser.compute(userId, (id, sessions) -> {
            if (sessions == null) {
                sessions = ConcurrentHashMap.newKeySet();
                first.set(true);
            }
            sessions.add(session);
            return sessions;
        });
        return first.get();
    }

    @Override
    public boolean unregister(String userId, WebSocketSession session) {
        AtomicBoolean last = new AtomicBoolean();
        sessionsByUser.computeIfPresent(userId, (id, sessions) -> {
            if (sessions.remove(session) && sessions.isEmpty()) {
                last.set(true);
                return null;
            }
            return sessions.isEmpty() ? null : sessions;
        });
        return last.get();
    }

    @Override
    public Collection<WebSocketSession> sessionsOf(String userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions == null ? List.of() : List.copyOf(sessions);
    }

    @Override
    public boolean isConnected(String userId) {
        return sessionsByUser.containsKey(userId);
    }

    @Override
    public Set<String> connectedUserIds() {
        return Set.copyOf(sessionsByUser.keySet());
    }

    @Override
    public int connectionCount() {
        return sessionsByUser.values().stream().mapToInt(Set::size).sum();
    }
}
