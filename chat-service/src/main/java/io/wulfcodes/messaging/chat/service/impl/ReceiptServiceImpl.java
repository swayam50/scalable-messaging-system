package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.exception.InvalidMessageException;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ReceiptResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.model.vo.ReceiptStatus;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.repository.custom.InboxCustomRepository.ReadPointer;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.ReceiptService;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Delivery receipts are not stored (they are a live signal); read pointers are, because they
 * drive unread badges and "seen" ticks after a reload.
 */
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ConversationService conversationService;
    private final InboxRepository inboxRepository;
    private final DeliveryService deliveryService;

    @Override
    public void notifyDelivered(MessageResponse message, String recipientId) {
        deliveryService.deliver(message.senderId(), ServerFrame.receipt(new ReceiptResponse(
                message.conversationId(), recipientId, message.messageId(), ReceiptStatus.DELIVERED)), null);
    }

    @Override
    public void markRead(String userId, String conversationId, String messageId, String originSessionId) {
        Conversation conversation = conversationService.requireParticipant(conversationId, userId);
        long id = parseMessageId(messageId);
        long writeTimeMicros = SnowflakeId.decode(id).timestamp() * 1_000;   // pointer only moves forward
        String peerId = conversation.peerOf(userId);

        inboxRepository.updateReadPointer(userId, conversationId, ReadPointer.OWN, id, writeTimeMicros);
        inboxRepository.updateReadPointer(peerId, conversationId, ReadPointer.PEER, id, writeTimeMicros);

        ServerFrame receipt = ServerFrame.receipt(new ReceiptResponse(conversationId, userId, messageId, ReceiptStatus.READ));
        deliveryService.deliver(peerId, receipt, null);              // sender sees blue ticks
        deliveryService.deliver(userId, receipt, originSessionId);   // my other tabs clear the badge
    }

    private static long parseMessageId(String messageId) {
        try {
            return Long.parseLong(messageId);
        } catch (NumberFormatException | NullPointerException e) {
            throw new InvalidMessageException("messageId must be a message id");
        }
    }
}
