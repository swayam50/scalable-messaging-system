package io.wulfcodes.messaging.chat.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable consistent-hash ring.
 * <p>
 * Every node is placed on a 64-bit ring at {@code virtualNodes} points (hash of "nodeId#i").
 * A key is owned by the first point clockwise from hash(key). Compared with {@code hash % N}:
 * <ul>
 *   <li>adding/removing a node only moves ~1/N of the keys (the arcs next to that node's points),
 *       instead of reshuffling almost everything;</li>
 *   <li>virtual nodes spread each node's share across the ring, so load stays even.</li>
 * </ul>
 * MD5 is used purely for its even, deterministic distribution (identical on every JVM/node),
 * not for security.
 */
public final class ConsistentHashRing {

    private final NavigableMap<Long, String> ring;
    private final Set<String> nodes;

    public ConsistentHashRing(Collection<String> nodeIds, int virtualNodes) {
        if (virtualNodes < 1) {
            throw new IllegalArgumentException("virtualNodes must be >= 1");
        }
        TreeMap<Long, String> points = new TreeMap<>();
        for (String nodeId : nodeIds) {
            for (int i = 0; i < virtualNodes; i++) {
                points.put(hash(nodeId + "#" + i), nodeId);
            }
        }
        this.ring = Collections.unmodifiableNavigableMap(points);
        this.nodes = Collections.unmodifiableSet(new TreeSet<>(nodeIds));
    }

    /**
     * @return the node owning {@code key}, or null if the ring is empty
     */
    public String ownerOf(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash(key));
        return (entry != null ? entry : ring.firstEntry()).getValue();   // wrap around
    }

    public Set<String> nodes() {
        return nodes;
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    static long hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(key.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
