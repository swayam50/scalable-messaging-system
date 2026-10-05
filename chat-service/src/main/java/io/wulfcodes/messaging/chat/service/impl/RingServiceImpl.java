package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.config.ChatProperties;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.model.vo.RingChangedEvent;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import io.wulfcodes.messaging.chat.util.ConsistentHashRing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the current ring as an immutable snapshot behind a volatile reference:
 * lookups (every message) are lock-free reads; updates (rare) swap in a new snapshot.
 */
@Slf4j
@Service
public class RingServiceImpl implements RingService {

    private record Snapshot(ConsistentHashRing ring, Map<String, NodeInfo> nodes) {
    }

    private final NodeInfo localNode;
    private final int virtualNodes;
    private final ApplicationEventPublisher events;
    private volatile Snapshot snapshot;

    public RingServiceImpl(ChatProperties properties, ApplicationEventPublisher events) {
        ChatProperties.Node node = properties.node();
        this.localNode = new NodeInfo(node.id(), node.grpcAddress(), node.wsUrl());
        this.virtualNodes = properties.cluster().virtualNodes();
        this.events = events;
        this.snapshot = build(Map.of(localNode.nodeId(), localNode));
    }

    @Override
    public NodeInfo ownerOf(String userId) {
        Snapshot current = snapshot;
        return current.nodes().get(current.ring().ownerOf(userId));
    }

    @Override
    public boolean isLocal(String userId) {
        return ownerOf(userId).nodeId().equals(localNode.nodeId());
    }

    @Override
    public NodeInfo localNode() {
        return localNode;
    }

    @Override
    public Collection<NodeInfo> nodes() {
        return snapshot.nodes().values();
    }

    @Override
    public synchronized void updateMembers(Collection<NodeInfo> liveNodes) {
        Map<String, NodeInfo> members = new LinkedHashMap<>();
        liveNodes.forEach(node -> members.put(node.nodeId(), node));
        members.put(localNode.nodeId(), localNode);   // we are alive even if our own heartbeat row lags

        if (members.equals(snapshot.nodes())) {
            return;
        }
        snapshot = build(members);
        log.info("Ring changed: {} node(s) {}", members.size(), members.keySet());
        events.publishEvent(new RingChangedEvent(Map.copyOf(members).keySet()));
    }

    private Snapshot build(Map<String, NodeInfo> members) {
        return new Snapshot(new ConsistentHashRing(members.keySet(), virtualNodes), Map.copyOf(members));
    }
}
