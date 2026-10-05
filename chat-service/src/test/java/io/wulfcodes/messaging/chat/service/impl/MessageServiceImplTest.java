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
import io.wulfcodes.messaging.chat.model.po.eo.AttachmentUdt;
import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.common.util.AttachmentSigner;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.repository.MessageRepository;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.ReceiptService;
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
import java.util.function.IntConsumer;

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
    private static final AttachmentSigner SIGNER = new AttachmentSigner("test-signing-secret-that-is-long-enough");

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private InboxRepository inboxRepository;
    @Mock
    private ConversationService conversationService;
    @Mock
    private DeliveryService deliveryService;
    @Mock
    private ReceiptService receiptService;

    private final MessageMapper messageMapper = new MessageMapperImpl();
    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ChatProperties properties = new ChatProperties(1, 4000, null, null, null, new ChatProperties.History(100), null, null, null);
        messageService = new MessageServiceImpl(messageRepository, inboxRepository, conversationService, deliveryService,
                receiptService, messageMapper, new SnowflakeIdGenerator(1, clock), properties, SIGNER, clock);
    }

    @Test
    void sendStoresMessageInTodaysBucketUpdatesBothInboxesAndDelivers() {
        when(conversationService.requireParticipant(CID, ALICE)).thenReturn(conversation(NOW));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        List<String> order = new java.util.ArrayList<>();
        MessageResponse response = messageService.send(ALICE, frame("hello"), "session-1", stored -> order.add("ack"));
        assertThat(order).containsExactly("ack");

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
        ArgumentCaptor<IntConsumer> onDelivered = ArgumentCaptor.forClass(IntConsumer.class);
        verify(deliveryService).deliver(eq(BOB), frame.capture(), eq(null), onDelivered.capture());
        verify(deliveryService).deliver(eq(ALICE), any(ServerFrame.class), eq("session-1"));
        assertThat(frame.getValue().type()).isEqualTo(FrameType.MESSAGE);

        // once bob's node reports a live session got it, alice gets a DELIVERED receipt
        onDelivered.getValue().accept(0);
        verify(receiptService, never()).notifyDelivered(any(), any());
        onDelivered.getValue().accept(1);
        verify(receiptService).notifyDelivered(response, BOB);
    }

    @Test
    void emptyBodyIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> messageService.send(ALICE, frame("   "), "s", stored -> { }))
                .isInstanceOf(InvalidMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void nonParticipantCannotSend() {
        when(conversationService.requireParticipant(CID, "MALLORY")).thenThrow(new ConversationNotFoundException(CID));

        assertThatThrownBy(() -> messageService.send("MALLORY", frame("hi"), "s", stored -> { }))
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

    // ---------------------------------------------------------------- media messages

    @Test
    void imageWithValidSignedAttachmentIsStoredWithItsMetadata() {
        when(conversationService.requireParticipant(CID, ALICE)).thenReturn(conversation(NOW));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.send(ALICE,
                mediaFrame(ContentType.IMAGE, SIGNER.sign(attachment(ALICE, CID, ContentType.IMAGE))), "s", stored -> { });

        assertThat(response.contentType()).isEqualTo(ContentType.IMAGE);
        assertThat(response.attachment().fileName()).isEqualTo("cat.png");
        assertThat(response.body()).isNull();   // caption is optional
    }

    @Test
    void forgedOrTamperedAttachmentIsRejected() {
        AttachmentDescriptor forged = new AttachmentSigner("an-attacker-secret-that-is-long-enough!!").sign(attachment(ALICE, CID, ContentType.IMAGE));
        assertThatThrownBy(() -> messageService.send(ALICE, mediaFrame(ContentType.IMAGE, forged), "s", stored -> { }))
                .isInstanceOf(InvalidMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void someoneElsesUploadOrOtherConversationOrWrongTypeIsRejected() {
        AttachmentDescriptor bobsUpload = SIGNER.sign(attachment(BOB, CID, ContentType.IMAGE));
        AttachmentDescriptor otherConversation = SIGNER.sign(attachment(ALICE, "OTHER", ContentType.IMAGE));
        AttachmentDescriptor image = SIGNER.sign(attachment(ALICE, CID, ContentType.IMAGE));

        assertThatThrownBy(() -> messageService.send(ALICE, mediaFrame(ContentType.IMAGE, bobsUpload), "s", s -> { }))
                .isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> messageService.send(ALICE, mediaFrame(ContentType.IMAGE, otherConversation), "s", s -> { }))
                .isInstanceOf(InvalidMessageException.class);
        assertThatThrownBy(() -> messageService.send(ALICE, mediaFrame(ContentType.VIDEO, image), "s", s -> { }))
                .isInstanceOf(InvalidMessageException.class);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void mediaPreviewUsesALabelAndTheCaption() {
        Message photo = new Message(new MessageKey(CID, 0, 1), ALICE, "look!", null, ContentType.IMAGE,
                new AttachmentUdt("A", "cat.png", "image/png", 10));
        Message file = new Message(new MessageKey(CID, 0, 2), ALICE, null, null, ContentType.FILE,
                new AttachmentUdt("B", "report.pdf", "application/pdf", 10));

        assertThat(MessageServiceImpl.previewOf(photo)).isEqualTo("📷 Photo · look!");
        assertThat(MessageServiceImpl.previewOf(file)).isEqualTo("📎 report.pdf");
    }

    private static AttachmentDescriptor attachment(String uploader, String conversationId, ContentType type) {
        return new AttachmentDescriptor("ATT", conversationId, uploader, type, "cat.png", "image/png", 2048, null);
    }

    private static ClientFrame mediaFrame(ContentType type, AttachmentDescriptor attachment) {
        return new ClientFrame(FrameType.SEND, CID, "c-9", null, null, null, type, attachment);
    }

    private static ClientFrame frame(String body) {
        return new ClientFrame(FrameType.SEND, CID, "c-1", body, null, null, null, null);
    }

    private static Conversation conversation(Instant createdAt) {
        return new Conversation(CID, Set.of(ALICE, BOB), createdAt);
    }

    private static Message message(long id) {
        return new Message(new MessageKey(CID, 0, id), ALICE, "m" + id, null, ContentType.TEXT, null);
    }
}
