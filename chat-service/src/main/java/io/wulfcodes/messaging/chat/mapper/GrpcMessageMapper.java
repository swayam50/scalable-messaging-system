package io.wulfcodes.messaging.chat.mapper;

import io.wulfcodes.messaging.chat.grpc.proto.ChatMessage;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * dto <-> protobuf. Hand-written: protobuf messages are immutable and built through builders,
 * and proto3 has no nulls (unset strings are ""), which MapStruct does not map naturally.
 */
@Component
public class GrpcMessageMapper {

    public ChatMessage toProto(MessageResponse message) {
        return ChatMessage.newBuilder()
                .setMessageId(message.messageId())
                .setConversationId(message.conversationId())
                .setSenderId(message.senderId())
                .setBody(message.body())
                .setSentAtMillis(message.sentAt().toEpochMilli())
                .setClientMessageId(nullToEmpty(message.clientMessageId()))
                .build();
    }

    public MessageResponse fromProto(ChatMessage message) {
        return new MessageResponse(
                message.getMessageId(),
                message.getConversationId(),
                message.getSenderId(),
                message.getBody(),
                Instant.ofEpochMilli(message.getSentAtMillis()),
                emptyToNull(message.getClientMessageId()));
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
