package io.wulfcodes.messaging.chat.websocket;

import io.wulfcodes.messaging.chat.model.vo.RingChangedEvent;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * When the ring changes (node joined/left), some users are now owned by a different node.
 * Their sockets on this node would stop receiving routed messages, so we close them with a
 * dedicated code; the client then calls /api/v1/connect and reconnects to the new owner.
 * Only ~1/N of users are affected thanks to consistent hashing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionRebalancer {

    /** Application-defined close code (4000-4999 range is reserved for applications). */
    public static final CloseStatus OWNER_CHANGED = new CloseStatus(4001, "owner node changed");

    private final RingService ringService;
    private final SessionRegistry sessionRegistry;

    @EventListener
    public void onRingChanged(RingChangedEvent event) {
        int moved = 0;
        for (String userId : sessionRegistry.connectedUserIds()) {
            if (ringService.isLocal(userId)) {
                continue;
            }
            for (WebSocketSession session : sessionRegistry.sessionsOf(userId)) {
                try {
                    session.close(OWNER_CHANGED);
                    moved++;
                } catch (IOException e) {
                    log.debug("Could not close session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
        if (moved > 0) {
            log.info("Ring changed to {}: asked {} session(s) to reconnect to their new owner", event.nodeIds(), moved);
        }
    }
}
