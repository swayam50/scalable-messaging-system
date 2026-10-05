package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.exception.ConversationNotFoundException;
import io.wulfcodes.messaging.chat.exception.InvalidConversationException;
import io.wulfcodes.messaging.chat.mapper.MessageMapper;
import io.wulfcodes.messaging.chat.model.dto.response.ConversationResponse;
import io.wulfcodes.messaging.chat.model.dto.response.InboxEntryResponse;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.model.po.DirectConversation;
import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import io.wulfcodes.messaging.chat.model.po.eo.DirectConversationKey;
import io.wulfcodes.messaging.chat.model.po.eo.InboxKey;
import io.wulfcodes.messaging.chat.repository.ConversationRepository;
import io.wulfcodes.messaging.chat.repository.DirectConversationRepository;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final DirectConversationRepository directConversationRepository;
    private final InboxRepository inboxRepository;
    private final MessageMapper messageMapper;
    private final UlidGenerator ulidGenerator;
    private final Clock clock;

    /** Bounded LRU cache of conversations (immutable participant sets). */
    private final Map<String, Conversation> participantCache = Collections.synchronizedMap(
            new LinkedHashMap<>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Conversation> eldest) {
                    return size() > PARTICIPANT_CACHE_SIZE;
                }
            });

    private static final int PARTICIPANT_CACHE_SIZE = 10_000;

    @Override
    public ConversationResponse getOrCreateDirect(String userId, String peerId) {
        if (userId.equals(peerId)) {
            throw new InvalidConversationException("Cannot start a conversation with yourself");
        }
        DirectConversationKey pairKey = DirectConversationKey.of(userId, peerId);

        DirectConversation existing = directConversationRepository.findById(pairKey).orElse(null);
        if (existing != null) {
            return toResponse(load(existing.getConversationId()), userId);
        }

        // Claim the pair with a lightweight transaction. If two requests race, only one INSERT
        // is applied; the loser reads the winner's conversation id instead of creating a duplicate.
        String candidateId = ulidGenerator.nextString();
        boolean created = directConversationRepository.insertIfNotExists(
                new DirectConversation(pairKey, candidateId));

        if (!created) {
            String winnerId = directConversationRepository.findById(pairKey)
                    .map(DirectConversation::getConversationId)
                    .orElseThrow(() -> new IllegalStateException("LWT reported existing row but none found"));
            return toResponse(load(winnerId), userId);
        }

        Conversation conversation = conversationRepository.save(Conversation.builder()
                .conversationId(candidateId)
                .participantIds(Set.of(userId, peerId))
                .createdAt(clock.instant().truncatedTo(ChronoUnit.MILLIS))   // Scylla stores ms precision
                .build());
        // Both users see the (still empty) conversation in their inbox right away
        inboxRepository.save(InboxEntry.builder().key(new InboxKey(userId, candidateId)).peerId(peerId).build());
        inboxRepository.save(InboxEntry.builder().key(new InboxKey(peerId, candidateId)).peerId(userId).build());

        return toResponse(conversation, userId);
    }

    @Override
    public List<InboxEntryResponse> getInbox(String userId) {
        // Small partition (one row per conversation), so sorting in memory is cheap.
        // Conversations without messages yet sort last.
        return inboxRepository.findByKeyUserId(userId).stream()
                .sorted(Comparator.comparing(InboxEntry::getLastMessageId,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(entry -> messageMapper.toInboxResponse(entry, userId))
                .toList();
    }

    /**
     * Called for every SEND / READ / TYPING frame. A conversation's participants never change,
     * so it is cached after the first load and later checks cost no database round trip.
     */
    @Override
    public Conversation requireParticipant(String conversationId, String userId) {
        Conversation conversation = participantCache.get(conversationId);
        if (conversation == null) {
            conversation = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new ConversationNotFoundException(conversationId));
            participantCache.put(conversationId, conversation);
        }
        if (!conversation.hasParticipant(userId)) {
            throw new ConversationNotFoundException(conversationId);
        }
        return conversation;
    }

    @Override
    public List<String> contactsOf(String userId) {
        return inboxRepository.findByKeyUserId(userId).stream().map(InboxEntry::getPeerId).distinct().toList();
    }

    private Conversation load(String conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ConversationNotFoundException(conversationId));
    }

    private static ConversationResponse toResponse(Conversation conversation, String userId) {
        return new ConversationResponse(conversation.getConversationId(), conversation.peerOf(userId),
                conversation.getCreatedAt());
    }
}
