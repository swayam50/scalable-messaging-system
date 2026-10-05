package io.wulfcodes.messaging.chat.model.dto.response;

/**
 * Which node the caller must open its WebSocket on (the owner of its user id on the ring).
 */
public record ConnectResponse(String nodeId, String wsUrl) {
}
