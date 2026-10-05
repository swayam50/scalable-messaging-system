package io.wulfcodes.messaging.media.service.spec;

public interface ConversationAccessService {

    /**
     * Fails unless the caller is a participant of the conversation.
     *
     * @param bearerToken the caller's own access token, relayed to chat-service
     */
    void requireParticipant(String conversationId, String bearerToken);
}
