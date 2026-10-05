package io.wulfcodes.messaging.chat.service.spec;

/**
 * Keeps this node registered in the cluster_nodes table and feeds live membership into the ring.
 */
public interface ClusterMembershipService {

    /** Re-writes this node's row with a fresh TTL. */
    void heartbeat();

    /** Reads all live nodes and updates the ring. */
    void refreshMembers();
}
