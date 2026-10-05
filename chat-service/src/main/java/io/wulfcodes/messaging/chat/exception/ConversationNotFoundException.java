package io.wulfcodes.messaging.chat.exception;

import org.springframework.http.HttpStatus;

/**
 * Also used when the caller is not a participant: answering 404 instead of 403
 * avoids revealing that a conversation with this id exists.
 */
public class ConversationNotFoundException extends ApiException {

    public ConversationNotFoundException(String conversationId) {
        super(HttpStatus.NOT_FOUND, "Conversation not found", "No conversation " + conversationId);
    }
}
