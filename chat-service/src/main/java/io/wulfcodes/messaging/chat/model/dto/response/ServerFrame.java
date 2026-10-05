package io.wulfcodes.messaging.chat.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.wulfcodes.messaging.chat.model.vo.FrameType;

/**
 * Frame sent by the server over the WebSocket (ACK, MESSAGE or ERROR).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerFrame(
        FrameType type,
        String clientMessageId,
        MessageResponse message,
        String error
) {

    public static ServerFrame ack(MessageResponse message) {
        return new ServerFrame(FrameType.ACK, message.clientMessageId(), message, null);
    }

    public static ServerFrame message(MessageResponse message) {
        return new ServerFrame(FrameType.MESSAGE, null, message, null);
    }

    public static ServerFrame error(String clientMessageId, String error) {
        return new ServerFrame(FrameType.ERROR, clientMessageId, null, error);
    }
}
