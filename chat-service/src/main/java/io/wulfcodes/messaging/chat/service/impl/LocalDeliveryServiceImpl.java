package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * Writes the frame to the user's sessions on this node.
 * Offline users simply miss the push; they fetch the message from history when they reconnect.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalDeliveryServiceImpl implements LocalDeliveryService {

    private final SessionRegistry sessionRegistry;
    private final JsonMapper jsonMapper;

    @Override
    public int deliverLocally(String userId, ServerFrame frame, String excludeSessionId) {
        TextMessage payload = new TextMessage(jsonMapper.writeValueAsString(frame));
        int delivered = 0;
        for (WebSocketSession session : sessionRegistry.sessionsOf(userId)) {
            if (session.getId().equals(excludeSessionId) || !session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(payload);
                delivered++;
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping dead session {} of user {}: {}", session.getId(), userId, e.getMessage());
                sessionRegistry.unregister(userId, session);
            }
        }
        return delivered;
    }
}
