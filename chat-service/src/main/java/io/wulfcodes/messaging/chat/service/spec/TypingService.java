package io.wulfcodes.messaging.chat.service.spec;

public interface TypingService {

    /** Relays a typing indicator to the other participant. Ephemeral: nothing is stored. */
    void typing(String userId, String conversationId, boolean typing);
}
