package io.wulfcodes.messaging.chat.model.dto.response;

import java.time.Instant;

/**
 * One conversation in the user's inbox. Message fields are null until the first message.
 *
 * @param lastReadMessageId     how far I have read
 * @param peerLastReadMessageId how far the other participant has read ("seen" ticks)
 * @param unread                the last message is from the peer and I have not read it
 */
public record InboxEntryResponse(
        String conversationId,
        String peerId,
        String lastMessageId,
        String lastSenderId,
        String preview,
        Instant lastMessageAt,
        String lastReadMessageId,
        String peerLastReadMessageId,
        boolean unread
) {
}
