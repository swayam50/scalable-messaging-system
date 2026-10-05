package io.wulfcodes.messaging.chat.mapper;

import io.wulfcodes.messaging.chat.grpc.proto.ChatMessage;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverRequest;
import io.wulfcodes.messaging.chat.grpc.proto.Presence;
import io.wulfcodes.messaging.chat.grpc.proto.Receipt;
import io.wulfcodes.messaging.chat.grpc.proto.Typing;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.PresenceResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ReceiptResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.dto.response.TypingResponse;
import io.wulfcodes.messaging.chat.model.vo.ReceiptStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * ServerFrame (dto) <-> protobuf DeliverRequest. Hand-written: protobuf messages are immutable,
 * built through builders, use a oneof for the event, and have no nulls (unset strings are ""),
 * none of which MapStruct maps naturally.
 */
@Component
public class GrpcMessageMapper {

    public DeliverRequest toRequest(String userId, ServerFrame frame, String excludeSessionId) {
        DeliverRequest.Builder request = DeliverRequest.newBuilder()
                .setUserId(userId)
                .setExcludeSessionId(nullToEmpty(excludeSessionId));
        switch (frame.type()) {
            case MESSAGE -> request.setMessage(toProto(frame.message()));
            case RECEIPT -> request.setReceipt(toProto(frame.receipt()));
            case TYPING -> request.setTyping(toProto(frame.typing()));
            case PRESENCE -> request.setPresence(toProto(frame.presence()));
            default -> throw new IllegalArgumentException("Frame type " + frame.type() + " is never forwarded");
        }
        return request.build();
    }

    public ServerFrame toFrame(DeliverRequest request) {
        return switch (request.getEventCase()) {
            case MESSAGE -> ServerFrame.message(fromProto(request.getMessage()));
            case RECEIPT -> ServerFrame.receipt(fromProto(request.getReceipt()));
            case TYPING -> ServerFrame.typing(fromProto(request.getTyping()));
            case PRESENCE -> ServerFrame.presence(fromProto(request.getPresence()));
            case EVENT_NOT_SET -> throw new IllegalArgumentException("DeliverRequest without event");
        };
    }

    // ---- message

    ChatMessage toProto(MessageResponse message) {
        return ChatMessage.newBuilder()
                .setMessageId(message.messageId())
                .setConversationId(message.conversationId())
                .setSenderId(message.senderId())
                .setBody(message.body())
                .setSentAtMillis(message.sentAt().toEpochMilli())
                .setClientMessageId(nullToEmpty(message.clientMessageId()))
                .build();
    }

    MessageResponse fromProto(ChatMessage message) {
        return new MessageResponse(message.getMessageId(), message.getConversationId(), message.getSenderId(),
                message.getBody(), Instant.ofEpochMilli(message.getSentAtMillis()), emptyToNull(message.getClientMessageId()));
    }

    // ---- receipt

    Receipt toProto(ReceiptResponse receipt) {
        return Receipt.newBuilder()
                .setConversationId(receipt.conversationId())
                .setUserId(receipt.userId())
                .setMessageId(receipt.messageId())
                .setStatus(receipt.status() == ReceiptStatus.READ ? Receipt.Status.READ : Receipt.Status.DELIVERED)
                .build();
    }

    ReceiptResponse fromProto(Receipt receipt) {
        return new ReceiptResponse(receipt.getConversationId(), receipt.getUserId(), receipt.getMessageId(),
                receipt.getStatus() == Receipt.Status.READ ? ReceiptStatus.READ : ReceiptStatus.DELIVERED);
    }

    // ---- typing

    Typing toProto(TypingResponse typing) {
        return Typing.newBuilder()
                .setConversationId(typing.conversationId())
                .setUserId(typing.userId())
                .setTyping(typing.typing())
                .build();
    }

    TypingResponse fromProto(Typing typing) {
        return new TypingResponse(typing.getConversationId(), typing.getUserId(), typing.getTyping());
    }

    // ---- presence

    Presence toProto(PresenceResponse presence) {
        return Presence.newBuilder()
                .setUserId(presence.userId())
                .setOnline(presence.online())
                .setLastSeenMillis(presence.lastSeen() == null ? 0 : presence.lastSeen().toEpochMilli())
                .build();
    }

    PresenceResponse fromProto(Presence presence) {
        return new PresenceResponse(presence.getUserId(), presence.getOnline(),
                presence.getLastSeenMillis() == 0 ? null : Instant.ofEpochMilli(presence.getLastSeenMillis()));
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
