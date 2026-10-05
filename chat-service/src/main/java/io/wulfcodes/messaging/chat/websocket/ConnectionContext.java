package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;

import java.util.function.Consumer;

/**
 * What a frame handler needs to know about the connection a frame arrived on.
 *
 * @param reply writes a frame back to THIS socket only (ACK / ERROR)
 */
public record ConnectionContext(String userId, String sessionId, Consumer<ServerFrame> reply) {

    public void reply(ServerFrame frame) {
        reply.accept(frame);
    }
}
