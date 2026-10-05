package io.wulfcodes.messaging.chat.model.dto.response;

import java.util.List;

/**
 * A page of history, newest first.
 *
 * @param nextBefore pass as {@code before} to fetch the next (older) page; null when there is no more
 */
public record MessagePageResponse(
        List<MessageResponse> messages,
        String nextBefore
) {
}
