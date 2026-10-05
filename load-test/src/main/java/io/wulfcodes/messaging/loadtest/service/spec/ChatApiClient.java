package io.wulfcodes.messaging.loadtest.service.spec;

/** The chat-service REST calls the load test needs during setup. */
public interface ChatApiClient {

    /** @return conversationId of the (created or existing) 1:1 conversation */
    String createConversation(String token, String peerId);

    /** @return {nodeId, wsUrl} of the node that owns the caller */
    String[] connect(String token);
}
