package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.PresenceResponse;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.po.UserPresence;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.repository.UserPresenceRepository;
import io.wulfcodes.messaging.chat.service.spec.ConversationService;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.PeerNodeClient;
import io.wulfcodes.messaging.chat.service.spec.PresenceService;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Presence without a central store: a user's owner node holds their sockets, so it is the source
 * of truth for "online". Changes are pushed to contacts; queries ask each user's owner node.
 * Only last-seen is persisted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final SessionRegistry sessionRegistry;
    private final RingService ringService;
    private final PeerNodeClient peerNodeClient;
    private final ConversationService conversationService;
    private final DeliveryService deliveryService;
    private final UserPresenceRepository userPresenceRepository;
    private final Clock clock;

    @Override
    public void userConnected(String userId) {
        broadcast(new PresenceResponse(userId, true, null));
    }

    @Override
    public void userDisconnected(String userId, boolean movingNodes) {
        if (movingNodes) {
            return;
        }
        Instant lastSeen = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        userPresenceRepository.save(new UserPresence(userId, lastSeen));
        broadcast(new PresenceResponse(userId, false, lastSeen));
    }

    @Override
    public List<PresenceResponse> presenceOf(List<String> userIds) {
        // Group users by owner node: one gRPC call per remote node, not per user.
        Map<NodeInfo, List<String>> byOwner = userIds.stream().distinct()
                .collect(Collectors.groupingBy(ringService::ownerOf));
        Set<String> online = new HashSet<>();
        byOwner.forEach((owner, users) -> {
            if (owner.nodeId().equals(ringService.localNode().nodeId())) {
                users.stream().filter(sessionRegistry::isConnected).forEach(online::add);
            } else {
                online.addAll(peerNodeClient.onlineAmong(owner, users));
            }
        });

        Map<String, Instant> lastSeen = userPresenceRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserPresence::getUserId, UserPresence::getLastSeen));
        Map<String, PresenceResponse> result = new LinkedHashMap<>();
        for (String userId : userIds) {
            boolean isOnline = online.contains(userId);
            result.put(userId, new PresenceResponse(userId, isOnline, isOnline ? null : lastSeen.get(userId)));
        }
        return List.copyOf(result.values());
    }

    private void broadcast(PresenceResponse presence) {
        try {
            ServerFrame frame = ServerFrame.presence(presence);
            conversationService.contactsOf(presence.userId())
                    .forEach(contact -> deliveryService.deliver(contact, frame, null));
        } catch (RuntimeException e) {
            log.debug("Presence broadcast for {} failed: {}", presence.userId(), e.getMessage());
        }
    }
}
