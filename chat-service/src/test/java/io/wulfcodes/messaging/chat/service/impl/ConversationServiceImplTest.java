package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.exception.ConversationNotFoundException;
import io.wulfcodes.messaging.chat.exception.InvalidConversationException;
import io.wulfcodes.messaging.chat.mapper.MessageMapperImpl;
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
import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private DirectConversationRepository directConversationRepository;
    @Mock
    private InboxRepository inboxRepository;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ConversationServiceImpl(conversationRepository, directConversationRepository, inboxRepository,
                new MessageMapperImpl(), new UlidGenerator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void pairKeyIsOrderIndependent() {
        DirectConversationKey ab = DirectConversationKey.of("A", "B");
        DirectConversationKey ba = DirectConversationKey.of("B", "A");
        assertThat(ab.getUserLow()).isEqualTo(ba.getUserLow()).isEqualTo("A");
        assertThat(ab.getUserHigh()).isEqualTo(ba.getUserHigh()).isEqualTo("B");
    }

    @Test
    void createsConversationAndBothInboxRowsWhenNew() {
        when(directConversationRepository.findById(any())).thenReturn(Optional.empty());
        when(directConversationRepository.insertIfNotExists(any())).thenReturn(true);
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        ConversationResponse response = service.getOrCreateDirect("A", "B");

        assertThat(response.peerId()).isEqualTo("B");
        assertThat(response.conversationId()).hasSize(26);
        verify(inboxRepository, times(2)).save(any(InboxEntry.class));
    }

    @Test
    void losingTheLightweightTransactionRaceReturnsTheWinnersConversation() {
        Conversation winner = new Conversation("WINNER", Set.of("A", "B"), NOW);
        when(directConversationRepository.findById(any()))
                .thenReturn(Optional.empty())                                     // first look: not there yet
                .thenReturn(Optional.of(new DirectConversation(DirectConversationKey.of("A", "B"), "WINNER")));
        when(directConversationRepository.insertIfNotExists(any())).thenReturn(false);  // someone else won
        when(conversationRepository.findById("WINNER")).thenReturn(Optional.of(winner));

        ConversationResponse response = service.getOrCreateDirect("A", "B");

        assertThat(response.conversationId()).isEqualTo("WINNER");
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void cannotChatWithYourself() {
        assertThatThrownBy(() -> service.getOrCreateDirect("A", "A")).isInstanceOf(InvalidConversationException.class);
    }

    @Test
    void outsiderGetsNotFoundRatherThanForbidden() {
        when(conversationRepository.findById("C")).thenReturn(Optional.of(new Conversation("C", Set.of("A", "B"), NOW)));

        assertThatThrownBy(() -> service.requireParticipant("C", "MALLORY"))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void inboxIsSortedNewestFirstWithEmptyConversationsLast() {
        when(inboxRepository.findByKeyUserId("A")).thenReturn(List.of(
                new InboxEntry(new InboxKey("A", "old"), "X", 100L, "X", "old msg", null, null),
                new InboxEntry(new InboxKey("A", "empty"), "Y", null, null, null, null, null),
                new InboxEntry(new InboxKey("A", "new"), "Z", 900L, "A", "new msg", 900L, null)));

        List<InboxEntryResponse> inbox = service.getInbox("A");

        assertThat(inbox).extracting(InboxEntryResponse::conversationId).containsExactly("new", "old", "empty");
        // "old": last message from X, never read -> unread; "new": my own message -> read
        assertThat(inbox).extracting(InboxEntryResponse::unread).containsExactly(false, true, false);
    }
}
