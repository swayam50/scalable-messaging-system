package io.wulfcodes.messaging.chat.model.vo;

import java.util.Set;

/**
 * Published (as a Spring application event) whenever the set of live nodes changes.
 */
public record RingChangedEvent(Set<String> nodeIds) {
}
