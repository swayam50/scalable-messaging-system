package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.exception.ApiException;
import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.service.spec.MessageService;
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
 * WebSocket entry point (the WebSocket equivalent of a controller): parses frames,
 * delegates to the service layer, and writes ACK / ERROR frames back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String SAFE_SESSION = "safeSession";
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;
    private final JsonMapper jsonMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // WebSocketSession.sendMessage is NOT thread-safe; the decorator serializes concurrent sends
        // (e.g. this user's own ACK and a message from someone else arriving at the same time).
        WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        session.getAttributes().put(SAFE_SESSION, safe);
        sessionRegistry.register(userId(session), safe);
        log.debug("User {} connected (session {}), {} connections on this node",
                userId(session), session.getId(), sessionRegistry.connectionCount());
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

        if (frame.type() != FrameType.SEND) {
            reply(session, ServerFrame.error(frame.clientMessageId(), "Unsupported frame type: " + frame.type()));
            return;
        }

        try {
            MessageResponse stored = messageService.send(userId(session), frame, session.getId());
            reply(session, ServerFrame.ack(stored));
        } catch (ApiException e) {
            reply(session, ServerFrame.error(frame.clientMessageId(), e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.unregister(userId(session), safeSession(session));
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
