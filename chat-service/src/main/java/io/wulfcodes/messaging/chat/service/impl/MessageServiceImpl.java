package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.config.ChatProperties;
import io.wulfcodes.messaging.chat.exception.InvalidMessageException;
import io.wulfcodes.messaging.chat.mapper.MessageMapper;
import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.MessagePageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import io.wulfcodes.messaging.chat.model.po.Message;
import io.wulfcodes.messaging.chat.model.po.eo.AttachmentUdt;
import io.wulfcodes.messaging.chat.model.po.eo.InboxKey;
import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.repository.MessageRepository;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.MessageService;
import io.wulfcodes.messaging.chat.service.spec.ReceiptService;
import io.wulfcodes.messaging.chat.util.BucketUtil;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.common.util.AttachmentSigner;
import io.wulfcodes.messaging.common.util.IdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    static final int PREVIEW_LENGTH = 100;

    private final MessageRepository messageRepository;
    private final InboxRepository inboxRepository;
    private final ConversationService conversationService;
    private final DeliveryService deliveryService;
    private final ReceiptService receiptService;
    private final MessageMapper messageMapper;
    private final IdGenerator idGenerator;
    private final ChatProperties properties;
    private final AttachmentSigner attachmentSigner;
    private final Clock clock;

    @Override
    public MessageResponse send(String senderId, ClientFrame frame, String originSessionId,
                                Consumer<MessageResponse> onStored) {
        validate(senderId, frame);
        Conversation conversation = conversationService.requireParticipant(frame.conversationId(), senderId);

        long messageId = idGenerator.nextId();
        AttachmentDescriptor attachment = frame.attachment();
        Message message = messageRepository.save(Message.builder()
                .key(new MessageKey(conversation.getConversationId(), BucketUtil.bucketOf(messageId), messageId))
                .senderId(senderId)
                .body(blankToNull(frame.body()))
                .clientMessageId(frame.clientMessageId())
                .contentType(frame.contentTypeOrText())
                .attachment(attachment == null ? null : new AttachmentUdt(attachment.attachmentId(),
                        attachment.fileName(), attachment.mimeType(), attachment.size()))
                .build());

        String recipientId = conversation.peerOf(senderId);
        updateInboxes(conversation.getConversationId(), senderId, recipientId, message);

        MessageResponse response = messageMapper.toResponse(message);
        onStored.accept(response);   // ACK first: the message is durable from here on

        ServerFrame messageFrame = ServerFrame.message(response);
        // Recipient: once at least one of their sessions got it (possibly on another node, reported
        // back in the gRPC response), the sender gets a DELIVERED receipt (second tick).
        deliveryService.deliver(recipientId, messageFrame, null, delivered -> {
            if (delivered > 0) {
                receiptService.notifyDelivered(response, recipientId);
            }
        });
        deliveryService.deliver(senderId, messageFrame, originSessionId);   // sender's other tabs
        return response;
    }

    @Override
    public MessagePageResponse history(String userId, String conversationId, String before, int limit) {
        Conversation conversation = conversationService.requireParticipant(conversationId, userId);
        int pageSize = Math.clamp(limit, 1, properties.history().maxPageSize());

        long beforeId = parseBefore(before);
        int bucket = before == null ? BucketUtil.bucketOf(clock.instant()) : BucketUtil.bucketOf(beforeId);
        int oldestBucket = BucketUtil.bucketOf(conversation.getCreatedAt());

        // Walk day-partitions backwards until the page is full or we pass the conversation's start.
        List<Message> page = new ArrayList<>(pageSize);
        while (page.size() < pageSize && bucket >= oldestBucket) {
            page.addAll(messageRepository.findPageBefore(conversationId, bucket, beforeId, pageSize - page.size()));
            bucket--;
        }

        String nextBefore = page.size() == pageSize
                ? Long.toString(page.getLast().getKey().getMessageId())
                : null;
        return new MessagePageResponse(messageMapper.toResponses(page), nextBefore);
    }

    private void updateInboxes(String conversationId, String senderId, String recipientId, Message message) {
        long messageId = message.getKey().getMessageId();
        long writeTimeMicros = SnowflakeId.decode(messageId).timestamp() * 1_000;
        String preview = previewOf(message);

        for (String owner : List.of(senderId, recipientId)) {
            String peer = owner.equals(senderId) ? recipientId : senderId;
            inboxRepository.upsertWithTimestamp(InboxEntry.builder()
                    .key(new InboxKey(owner, conversationId))
                    .peerId(peer)
                    .lastMessageId(messageId)
                    .lastSenderId(senderId)
                    .preview(preview)
                    // you have obviously read your own message (null = column left untouched)
                    .lastReadMessageId(owner.equals(senderId) ? messageId : null)
                    .build(), writeTimeMicros);
        }
    }

    private void validate(String senderId, ClientFrame frame) {
        if (frame.conversationId() == null || frame.conversationId().isBlank()) {
            throw new InvalidMessageException("conversationId is required");
        }
        if (frame.body() != null && frame.body().length() > properties.maxMessageLength()) {
            throw new InvalidMessageException("Message exceeds " + properties.maxMessageLength() + " characters");
        }
        ContentType type = frame.contentTypeOrText();
        if (type == ContentType.TEXT) {
            if (frame.body() == null || frame.body().isBlank()) {
                throw new InvalidMessageException("Message body must not be empty");
            }
            if (frame.attachment() != null) {
                throw new InvalidMessageException("Text messages cannot carry an attachment");
            }
            return;
        }
        // Media: the attachment must be exactly what media-service verified and signed for THIS
        // sender, THIS conversation and THIS type. Checked locally via HMAC: no call to media-service.
        AttachmentDescriptor attachment = frame.attachment();
        if (attachment == null || !attachmentSigner.isValid(attachment)) {
            throw new InvalidMessageException("Attachment is missing or its signature is invalid");
        }
        if (!senderId.equals(attachment.uploaderId())
                || !frame.conversationId().equals(attachment.conversationId())
                || attachment.contentType() != type) {
            throw new InvalidMessageException("Attachment does not belong to this sender, conversation or type");
        }
    }

    /** Inbox preview: text, or a label for media (plus the caption if any). */
    static String previewOf(Message message) {
        ContentType type = message.getContentType() == null ? ContentType.TEXT : message.getContentType();
        String text = switch (type) {
            case TEXT -> message.getBody();
            case IMAGE -> label("📷 Photo", message.getBody());
            case VIDEO -> label("🎬 Video", message.getBody());
            case AUDIO -> label("🎵 Audio", message.getBody());
            case FILE -> label("📎 " + message.getAttachment().getFileName(), message.getBody());
        };
        return text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) : text;
    }

    private static String label(String label, String caption) {
        return caption == null || caption.isBlank() ? label : label + " · " + caption;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static long parseBefore(String before) {
        if (before == null) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(before);
        } catch (NumberFormatException e) {
            throw new InvalidMessageException("before must be a message id");
        }
    }
}
