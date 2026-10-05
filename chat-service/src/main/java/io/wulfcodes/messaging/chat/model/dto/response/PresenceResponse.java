package io.wulfcodes.messaging.chat.model.dto.response;

import java.time.Instant;

/**
 * @param lastSeen when the user was last connected; null while online or if never seen
 */
public record PresenceResponse(String userId, boolean online, Instant lastSeen) {
}
