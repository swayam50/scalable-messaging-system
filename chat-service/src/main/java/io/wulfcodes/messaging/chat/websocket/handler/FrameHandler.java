package io.wulfcodes.messaging.chat.websocket.handler;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.websocket.ConnectionContext;

/**
 * Strategy for one client frame type. Each implementation is a Spring bean; the
 * {@code FrameDispatcher} collects them all, so supporting a new frame type means adding
 * one class and touching nothing else (open/closed principle).
 */
public interface FrameHandler {

    FrameType type();

    void handle(ConnectionContext connection, ClientFrame frame);
}
