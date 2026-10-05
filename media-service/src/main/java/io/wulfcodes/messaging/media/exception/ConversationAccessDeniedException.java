package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

public class ConversationAccessDeniedException extends ApiException {

    public ConversationAccessDeniedException(String conversationId) {
        super(HttpStatus.NOT_FOUND, "Conversation not found", "No conversation " + conversationId);
    }
}
