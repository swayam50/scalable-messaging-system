package io.wulfcodes.messaging.chat.websocket.handler;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.service.spec.ReceiptService;
import io.wulfcodes.messaging.chat.websocket.ConnectionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** READ: move the reader's read pointer and send READ receipts. */
@Component
@RequiredArgsConstructor
public class ReadFrameHandler implements FrameHandler {

    private final ReceiptService receiptService;

    @Override
    public FrameType type() {
        return FrameType.READ;
    }

    @Override
    public void handle(ConnectionContext connection, ClientFrame frame) {
        receiptService.markRead(connection.userId(), frame.conversationId(), frame.messageId(), connection.sessionId());
    }
}
