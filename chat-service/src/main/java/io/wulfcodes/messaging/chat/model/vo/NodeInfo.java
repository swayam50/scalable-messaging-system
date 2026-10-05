package io.wulfcodes.messaging.chat.model.vo;

/**
 * Immutable description of a chat-service node in the ring.
 */
public record NodeInfo(String nodeId, String grpcAddress, String wsUrl) {
}
