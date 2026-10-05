package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.config.ChatProperties;
import io.wulfcodes.messaging.chat.exception.ConversationNotFoundException;
import io.wulfcodes.messaging.chat.exception.InvalidMessageException;
import io.wulfcodes.messaging.chat.mapper.MessageMapper;
import io.wulfcodes.messaging.chat.mapper.MessageMapperImpl;
import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.MessagePageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import io.wulfcodes.messaging.chat.model.po.Message;
import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.repository.MessageRepository;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.util.BucketUtil;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import io.wulfcodes.messaging.common.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final String ALICE = "ALICE";
    private static final String BOB = "BOB";
    private static final String CID = "CONV";

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private InboxRepository inboxRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private DeliveryService deliveryService;

    private final MessageMapper messageMapper = new MessageMapperImpl();
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ChatProperties properties = new ChatProperties(1, 4000, null, null, null, new ChatProperties.History(100), null, null);
        messageService = new MessageServiceImpl(messageRepository, inboxRepository, conversationService, deliveryService,
                messageMapper, new SnowflakeIdGenerator(1, clock), properties, clock);
    }

    @Test
    void sendStoresMessageInTodaysBucketUpdatesBothInboxesAndDelivers() {
        when(conversationService.requireParticipant(CID, ALICE)).thenReturn(conversation(NOW));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.send(ALICE, frame("hello"), "session-1");

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        long id = saved.getValue().getKey().getMessageId();
        assertThat(saved.getValue().getKey().getBucket()).isEqualTo(BucketUtil.bucketOf(NOW));
        assertThat(response.messageId()).isEqualTo(Long.toString(id));
        assertThat(response.sentAt()).isEqualTo(NOW);

        // both inboxes, written with the message time as the Cassandra write timestamp
        long micros = SnowflakeId.decode(id).timestamp() * 1_000;
        verify(inboxRepository, times(2)).upsertWithTimestamp(any(InboxEntry.class), eq(micros));

        // recipient gets it; sender's other tabs too, but not the originating session
        ArgumentCaptor<ServerFrame> frame = ArgumentCaptor.forClass(ServerFrame.class);
        verify(deliveryService).deliver(eq(BOB), frame.capture(), eq(null));
        verify(deliveryService).deliver(eq(ALICE), any(ServerFrame.class), eq("session-1"));
        assertThat(frame.getValue().type()).isEqualTo(FrameType.MESSAGE);
    }

    @Test
    void emptyBodyIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> messageService.send(ALICE, frame("   "), "s"))
                .isInstanceOf(InvalidMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void nonParticipantCannotSend() {
        when(conversationService.requireParticipant(CID, "MALLORY")).thenThrow(new ConversationNotFoundException(CID));

        assertThatThrownBy(() -> messageService.send("MALLORY", frame("hi"), "s"))
                .isInstanceOf(ConversationNotFoundException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void historyWalksBackAcrossDayBucketsUntilPageIsFull() {
        // conversation started 2 days ago; today's bucket has 1 message, yesterday's has 2
        when(conversationService.requireParticipant(CID, ALICE)).thenReturn(conversation(NOW.minus(2, ChronoUnit.DAYS)));
        int today = BucketUtil.bucketOf(NOW);
        when(messageRepository.findPageBefore(eq(CID), eq(today), anyLong(), anyInt()))
                .thenReturn(List.of(message(30)));
        when(messageRepository.findPageBefore(eq(CID), eq(today - 1), anyLong(), anyInt()))
                .thenReturn(List.of(message(20), message(10)));

        MessagePageResponse page = messageService.history(ALICE, CID, null, 3);

        assertThat(page.messages()).extracting(MessageResponse::messageId).containsExactly("30", "20", "10");
        assertThat(page.nextBefore()).isEqualTo("10");
        verify(messageRepository, never()).findPageBefore(eq(CID), eq(today - 2), anyLong(), anyInt());
    }

    @Test
    void historyStopsAtConversationStartWhenPageIsNotFull() {
        when(conversationService.requireParticipant(CID, ALICE)).thenReturn(conversation(NOW));
        when(messageRepository.findPageBefore(eq(CID), anyInt(), anyLong(), anyInt())).thenReturn(List.of(message(5)));

        MessagePageResponse page = messageService.history(ALICE, CID, null, 50);

        assertThat(page.messages()).hasSize(1);
        assertThat(page.nextBefore()).isNull();
        verify(messageRepository, times(1)).findPageBefore(eq(CID), anyInt(), anyLong(), anyInt());
    }

    private static ClientFrame frame(String body) {
        return new ClientFrame(FrameType.SEND, CID, "c-1", body);
    }

    private static Conversation conversation(Instant createdAt) {
        return new Conversation(CID, Set.of(ALICE, BOB), createdAt);
    }

    private static Message message(long id) {
        return new Message(new MessageKey(CID, 0, id), ALICE, "m" + id, null);
    }
}
