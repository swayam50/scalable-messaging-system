package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.vo.NodeInfo;

import java.util.Collection;

/**
 * The current consistent-hash ring of live chat nodes, as seen by this node.
 */
public interface RingService {

    /** Node that owns {@code userId} (always non-null: this node is always part of its own ring). */
    NodeInfo ownerOf(String userId);

    boolean isLocal(String userId);

    NodeInfo localNode();

    Collection<NodeInfo> nodes();

    /** Replaces the membership; publishes a RingChangedEvent if the node set changed. */
    void updateMembers(Collection<NodeInfo> liveNodes);
}
