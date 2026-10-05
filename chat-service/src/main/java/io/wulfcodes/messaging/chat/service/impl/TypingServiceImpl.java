package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.dto.response.TypingResponse;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TypingServiceImpl implements TypingService {

    private final ConversationService conversationService;
    private final DeliveryService deliveryService;

    @Override
    public void typing(String userId, String conversationId, boolean typing) {
        Conversation conversation = conversationService.requireParticipant(conversationId, userId);
        deliveryService.deliver(conversation.peerOf(userId),
                ServerFrame.typing(new TypingResponse(conversationId, userId, typing)), null);
    }
}
