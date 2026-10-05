package io.wulfcodes.messaging.loadtest.model.vo;

/** A simulated user: id, its peer, their conversation, and the node it is connected to. */
public record VirtualUserSpec(String userId, String peerId, String conversationId, String nodeId, String wsUrl) {
}
