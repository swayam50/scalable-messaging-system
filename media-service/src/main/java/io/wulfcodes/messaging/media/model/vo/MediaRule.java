package io.wulfcodes.messaging.media.model.vo;

import io.wulfcodes.messaging.common.model.vo.ContentType;

/**
 * Immutable outcome of classifying an upload: which content type it is and its size limit.
 */
public record MediaRule(ContentType contentType, long maxBytes) {
}
