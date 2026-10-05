package io.wulfcodes.messaging.chat.websocket.handler;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.service.spec.MessageService;
import io.wulfcodes.messaging.chat.websocket.ConnectionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** SEND: store the message, ACK the sender with the server-assigned id, then deliver it. */
@Component
@RequiredArgsConstructor
public class SendFrameHandler implements FrameHandler {

    private final MessageService messageService;

    @Override
    public FrameType type() {
        return FrameType.SEND;
    }

    @Override
    public void handle(ConnectionContext connection, ClientFrame frame) {
        messageService.send(connection.userId(), frame, connection.sessionId(),
                stored -> connection.reply(ServerFrame.ack(stored)));
    }
}
