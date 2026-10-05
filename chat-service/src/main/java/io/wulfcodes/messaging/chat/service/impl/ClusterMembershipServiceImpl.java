package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.config.ChatProperties;
import io.wulfcodes.messaging.chat.model.po.ClusterNode;
import io.wulfcodes.messaging.chat.model.po.eo.ClusterNodeKey;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.repository.ClusterNodeRepository;
import io.wulfcodes.messaging.chat.service.spec.ClusterMembershipService;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Membership via ScyllaDB TTL rows (no ZooKeeper/Redis needed):
 * <ul>
 *   <li>heartbeat: every few seconds, upsert our row USING TTL (e.g. 15s);</li>
 *   <li>crash: heartbeats stop, the row expires, peers drop us from the ring;</li>
 *   <li>clean shutdown: we delete our row so peers react within one refresh interval.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterMembershipServiceImpl implements ClusterMembershipService, SmartLifecycle {

    private final ClusterNodeRepository clusterNodeRepository;
    private final RingService ringService;
    private final ChatProperties properties;

    private volatile boolean running;

    @Override
    @Scheduled(fixedDelayString = "${chat.cluster.heartbeat-interval}")
    public void heartbeat() {
        if (!running) {
            return;
        }
        NodeInfo self = ringService.localNode();
        try {
            clusterNodeRepository.upsertWithTtl(ClusterNode.builder()
                    .key(new ClusterNodeKey(properties.cluster().name(), self.nodeId()))
                    .grpcAddress(self.grpcAddress())
                    .wsUrl(self.wsUrl())
                    .build(), properties.cluster().memberTtl());
        } catch (RuntimeException e) {
            log.warn("Heartbeat failed: {}", e.getMessage());
        }
    }

    @Override
    @Scheduled(fixedDelayString = "${chat.cluster.refresh-interval}")
    public void refreshMembers() {
        if (!running) {
            return;
        }
        try {
            List<NodeInfo> live = clusterNodeRepository.findByKeyCluster(properties.cluster().name()).stream()
                    .map(node -> new NodeInfo(node.getKey().getNodeId(), node.getGrpcAddress(), node.getWsUrl()))
                    .toList();
            ringService.updateMembers(live);
        } catch (RuntimeException e) {
            // keep routing with the last known ring rather than failing every message
            log.warn("Membership refresh failed, keeping previous ring: {}", e.getMessage());
        }
    }

    // ---- lifecycle: join immediately on startup, leave immediately on shutdown ----

    @Override
    public void start() {
        running = true;
        heartbeat();
        refreshMembers();
        log.info("Joined cluster '{}' as {}", properties.cluster().name(), ringService.localNode());
    }

    @Override
    public void stop() {
        running = false;
        try {
            clusterNodeRepository.deleteById(
                    new ClusterNodeKey(properties.cluster().name(), ringService.localNode().nodeId()));
            log.info("Left cluster '{}'", properties.cluster().name());
        } catch (RuntimeException e) {
            log.warn("Could not deregister (row will expire via TTL): {}", e.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Lifecycle phase: stop() runs before lower-phase components such as the CQL session are shut
     * down, so the deregistering DELETE can still reach ScyllaDB.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 100;
    }
}
