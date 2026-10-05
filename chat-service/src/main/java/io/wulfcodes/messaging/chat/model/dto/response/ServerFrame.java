package io.wulfcodes.messaging.chat.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.wulfcodes.messaging.chat.model.vo.FrameType;

/**
 * Frame sent by the server over the WebSocket. Exactly one payload is set, matching {@code type}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServerFrame(
        FrameType type,
        String clientMessageId,
        MessageResponse message,
        ReceiptResponse receipt,
        TypingResponse typing,
        PresenceResponse presence,
        String error
) {

    public static ServerFrame ack(MessageResponse message) {
        return new ServerFrame(FrameType.ACK, message.clientMessageId(), message, null, null, null, null);
    }

    public static ServerFrame message(MessageResponse message) {
        return new ServerFrame(FrameType.MESSAGE, null, message, null, null, null, null);
    }

    public static ServerFrame receipt(ReceiptResponse receipt) {
        return new ServerFrame(FrameType.RECEIPT, null, null, receipt, null, null, null);
    }

    public static ServerFrame typing(TypingResponse typing) {
        return new ServerFrame(FrameType.TYPING, null, null, null, typing, null, null);
    }

    public static ServerFrame presence(PresenceResponse presence) {
        return new ServerFrame(FrameType.PRESENCE, null, null, null, null, presence, null);
    }

    public static ServerFrame error(String clientMessageId, String error) {
        return new ServerFrame(FrameType.ERROR, clientMessageId, null, null, null, null, error);
    }
}
