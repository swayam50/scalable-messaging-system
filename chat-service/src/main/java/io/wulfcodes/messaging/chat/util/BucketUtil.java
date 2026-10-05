package io.wulfcodes.messaging.chat.util;

import io.wulfcodes.messaging.common.model.vo.SnowflakeId;

import java.time.Duration;
import java.time.Instant;

/**
 * Message partitions are bucketed by day: (conversation_id, bucket).
 * A day keeps partitions small for busy chats while paging rarely has to cross many buckets.
 */
public final class BucketUtil {

    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private BucketUtil() {
    }

    public static int bucketOf(long snowflakeId) {
        return (int) (SnowflakeId.decode(snowflakeId).timestamp() / MILLIS_PER_DAY);
    }

    public static int bucketOf(Instant instant) {
        return (int) (instant.toEpochMilli() / MILLIS_PER_DAY);
    }
}
