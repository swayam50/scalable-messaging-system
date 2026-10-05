package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.exception.InvalidMessageException;
import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.websocket.handler.FrameHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrameDispatcherTest {

    private final List<ServerFrame> replies = new ArrayList<>();
    private final ConnectionContext connection = new ConnectionContext("ALICE", "s-1", replies::add);

    @Test
    void routesEachFrameToTheHandlerForItsType() {
        List<FrameType> handled = new ArrayList<>();
        FrameDispatcher dispatcher = new FrameDispatcher(List.of(
                handler(FrameType.SEND, handled), handler(FrameType.READ, handled), handler(FrameType.TYPING, handled)));

        dispatcher.dispatch(connection, frame(FrameType.READ));
        dispatcher.dispatch(connection, frame(FrameType.TYPING));
        dispatcher.dispatch(connection, frame(FrameType.SEND));

        assertThat(handled).containsExactly(FrameType.READ, FrameType.TYPING, FrameType.SEND);
        assertThat(replies).isEmpty();
    }

    @Test
    void unknownOrServerOnlyTypeGetsAnErrorFrame() {
        FrameDispatcher dispatcher = new FrameDispatcher(List.of());

        dispatcher.dispatch(connection, frame(FrameType.ACK));

        assertThat(replies).singleElement().extracting(ServerFrame::type).isEqualTo(FrameType.ERROR);
    }

    @Test
    void businessErrorsBecomeErrorFramesWithTheClientMessageId() {
        FrameHandler failing = new FrameHandler() {
            public FrameType type() { return FrameType.SEND; }
            public void handle(ConnectionContext c, ClientFrame f) { throw new InvalidMessageException("empty"); }
        };
        new FrameDispatcher(List.of(failing)).dispatch(connection, new ClientFrame(FrameType.SEND, "C", "c-7", "", null, null, null, null));

        assertThat(replies).singleElement().satisfies(reply -> {
            assertThat(reply.type()).isEqualTo(FrameType.ERROR);
            assertThat(reply.clientMessageId()).isEqualTo("c-7");
        });
    }

    @Test
    void duplicateHandlersFailFast() {
        assertThatThrownBy(() -> new FrameDispatcher(List.of(handler(FrameType.SEND, null), handler(FrameType.SEND, null))))
                .isInstanceOf(IllegalStateException.class);
    }

    private static FrameHandler handler(FrameType type, List<FrameType> handled) {
        return new FrameHandler() {
            public FrameType type() { return type; }
            public void handle(ConnectionContext c, ClientFrame f) { handled.add(f.type()); }
        };
    }

    private static ClientFrame frame(FrameType type) {
        return new ClientFrame(type, "C", null, "hi", "1", true, null, null);
    }
}
