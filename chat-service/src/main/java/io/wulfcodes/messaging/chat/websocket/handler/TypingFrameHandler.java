package io.wulfcodes.messaging.chat.websocket.handler;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.service.spec.TypingService;
import io.wulfcodes.messaging.chat.websocket.ConnectionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TYPING: relay a typing indicator to the other participant. */
@Component
@RequiredArgsConstructor
public class TypingFrameHandler implements FrameHandler {

    private final TypingService typingService;

    @Override
    public FrameType type() {
        return FrameType.TYPING;
    }

    @Override
    public void handle(ConnectionContext connection, ClientFrame frame) {
        typingService.typing(connection.userId(), frame.conversationId(), Boolean.TRUE.equals(frame.typing()));
    }
}
