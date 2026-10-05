package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import io.wulfcodes.messaging.chat.service.spec.NodeForwarder;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingDeliveryServiceImplTest {

    private static final NodeInfo SELF = new NodeInfo("chat-1", "chat-1:9090", "ws://a");
    private static final NodeInfo OTHER = new NodeInfo("chat-2", "chat-2:9090", "ws://b");
    private static final MessageResponse MESSAGE =
            new MessageResponse("1", "C", "ALICE", "hi", Instant.EPOCH, "c-1");

    @Mock
    private RingService ringService;
    @Mock
    private LocalDeliveryService localDeliveryService;
    @Mock
    private NodeForwarder nodeForwarder;

    private RoutingDeliveryServiceImpl routing;

    @BeforeEach
    void setUp() {
        routing = new RoutingDeliveryServiceImpl(ringService, localDeliveryService, nodeForwarder);
        when(ringService.localNode()).thenReturn(SELF);
    }

    @Test
    void userOwnedByThisNodeIsDeliveredLocally() {
        when(ringService.ownerOf("BOB")).thenReturn(SELF);

        routing.deliver("BOB", ServerFrame.message(MESSAGE), null);

        verify(localDeliveryService).deliverLocally("BOB", ServerFrame.message(MESSAGE), null);
        verify(nodeForwarder, never()).forward(any(), anyString(), any(), any());
    }

    @Test
    void userOwnedByAnotherNodeIsForwardedOverGrpc() {
        when(ringService.ownerOf("BOB")).thenReturn(OTHER);

        routing.deliver("BOB", ServerFrame.message(MESSAGE), "s-9");

        verify(nodeForwarder).forward(OTHER, "BOB", MESSAGE, "s-9");
        verify(localDeliveryService, never()).deliverLocally(anyString(), any(), any());
    }
}
