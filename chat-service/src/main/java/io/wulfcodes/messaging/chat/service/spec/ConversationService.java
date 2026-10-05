package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.ConversationResponse;
import io.wulfcodes.messaging.chat.model.dto.response.InboxEntryResponse;
import io.wulfcodes.messaging.chat.model.po.Conversation;

import java.util.List;

public interface ConversationService {

    /** Returns the existing 1:1 conversation with {@code peerId}, creating it exactly once if needed. */
    ConversationResponse getOrCreateDirect(String userId, String peerId);

    /** The user's conversations, most recently active first. */
    List<InboxEntryResponse> getInbox(String userId);

    /** Loads a conversation, failing with 404 if it does not exist or the user is not a participant. */
    Conversation requireParticipant(String conversationId, String userId);

    /** Everyone the user has a conversation with (used to fan out presence changes). */
    List<String> contactsOf(String userId);
}
