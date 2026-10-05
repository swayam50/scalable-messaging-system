package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.exception.InvalidMessageException;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.po.Conversation;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.model.vo.ReceiptStatus;
import io.wulfcodes.messaging.chat.repository.InboxRepository;
import io.wulfcodes.messaging.chat.repository.custom.InboxCustomRepository.ReadPointer;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import io.wulfcodes.messaging.common.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private ConversationService conversationService;
    @Mock
    private InboxRepository inboxRepository;
    @Mock
    private DeliveryService deliveryService;
    @InjectMocks
    private ReceiptServiceImpl receiptService;

    @Test
    void readMovesBothPointersUsingTheMessageTimeAndNotifiesPeerAndOwnTabs() {
        when(conversationService.requireParticipant("C", "BOB")).thenReturn(new Conversation("C", Set.of("ALICE", "BOB"), Instant.EPOCH));
        long messageId = new SnowflakeIdGenerator(1).nextId();
        long micros = SnowflakeId.decode(messageId).timestamp() * 1_000;

        receiptService.markRead("BOB", "C", Long.toString(messageId), "bob-tab-1");

        verify(inboxRepository).updateReadPointer("BOB", "C", ReadPointer.OWN, messageId, micros);
        verify(inboxRepository).updateReadPointer("ALICE", "C", ReadPointer.PEER, messageId, micros);
        ArgumentCaptor<ServerFrame> toAlice = ArgumentCaptor.forClass(ServerFrame.class);
        verify(deliveryService).deliver(eq("ALICE"), toAlice.capture(), eq(null));
        verify(deliveryService).deliver(eq("BOB"), any(ServerFrame.class), eq("bob-tab-1"));
        assertThat(toAlice.getValue().type()).isEqualTo(FrameType.RECEIPT);
        assertThat(toAlice.getValue().receipt().status()).isEqualTo(ReceiptStatus.READ);
    }

    @Test
    void garbageMessageIdIsRejected() {
        when(conversationService.requireParticipant("C", "BOB")).thenReturn(new Conversation("C", Set.of("ALICE", "BOB"), Instant.EPOCH));

        assertThatThrownBy(() -> receiptService.markRead("BOB", "C", "not-a-number", "s"))
                .isInstanceOf(InvalidMessageException.class);
    }
}
