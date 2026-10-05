package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.exception.ApiException;
import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.websocket.handler.FrameHandler;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Routes each client frame to the handler registered for its type. Handlers are discovered
 * from the Spring context; two handlers for the same type fail fast at startup.
 */
@Component
public class FrameDispatcher {

    private final Map<FrameType, FrameHandler> handlers = new EnumMap<>(FrameType.class);

    public FrameDispatcher(List<FrameHandler> frameHandlers) {
        for (FrameHandler handler : frameHandlers) {
            FrameHandler previous = handlers.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers for frame type " + handler.type());
            }
        }
    }

    public void dispatch(ConnectionContext connection, ClientFrame frame) {
        FrameHandler handler = frame.type() == null ? null : handlers.get(frame.type());
        if (handler == null) {
            connection.reply(ServerFrame.error(frame.clientMessageId(), "Unsupported frame type: " + frame.type()));
            return;
        }
        try {
            handler.handle(connection, frame);
        } catch (ApiException e) {
            connection.reply(ServerFrame.error(frame.clientMessageId(), e.getMessage()));
        }
    }
}
