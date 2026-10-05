package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.service.spec.PresenceService;
import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/**
 * WebSocket entry point: connection lifecycle (registry + presence) and frame parsing.
 * What to do with each frame type is decided by {@link FrameDispatcher} and its handlers.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String SAFE_SESSION = "safeSession";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final FrameDispatcher frameDispatcher;
    private final SessionRegistry sessionRegistry;
    private final PresenceService presenceService;
    private final JsonMapper jsonMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // WebSocketSession.sendMessage is NOT thread-safe; the decorator serializes concurrent sends
        // (e.g. this user's own ACK and a message from someone else arriving at the same time).
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        session.getAttributes().put(SAFE_SESSION, safe);
        if (sessionRegistry.register(userId(session), safe)) {
            presenceService.userConnected(userId(session));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        ClientFrame frame;
        try {
            frame = jsonMapper.readValue(message.getPayload(), ClientFrame.class);
        } catch (JacksonException e) {
            reply(session, ServerFrame.error(null, "Malformed frame"));
            return;
        }
        frameDispatcher.dispatch(new ConnectionContext(userId(session), session.getId(), f -> reply(session, f)), frame);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (sessionRegistry.unregister(userId(session), safeSession(session))) {
            boolean movingNodes = status.getCode() == ConnectionRebalancer.OWNER_CHANGED.getCode();
            presenceService.userDisconnected(userId(session), movingNodes);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("Transport error on session {}: {}", session.getId(), exception.getMessage());
    }

    private void reply(WebSocketSession session, ServerFrame frame) {
        try {
            safeSession(session).sendMessage(new TextMessage(jsonMapper.writeValueAsString(frame)));
        } catch (IOException e) {
            log.debug("Could not reply on session {}: {}", session.getId(), e.getMessage());
        }
    }

    private static WebSocketSession safeSession(WebSocketSession session) {
        Object safe = session.getAttributes().get(SAFE_SESSION);
        return safe instanceof WebSocketSession s ? s : session;
    }

    private static String userId(WebSocketSession session) {
        return (String) session.getAttributes().get(JwtHandshakeInterceptor.USER_ID);
    }
}
