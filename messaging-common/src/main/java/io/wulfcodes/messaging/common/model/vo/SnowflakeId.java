package io.wulfcodes.messaging.common.model.vo;

import io.wulfcodes.messaging.common.util.SnowflakeIdGenerator;

import java.time.Instant;

/**
 * Value object holding the decoded parts of a Snowflake id.
 *
 * @param timestamp milliseconds since the Unix epoch
 * @param workerId  node that generated the id (0-1023)
 * @param sequence  per-millisecond counter (0-4095)
 */
public record SnowflakeId(long timestamp, long workerId, long sequence) {

    public static SnowflakeId decode(long id) {
        long timestamp = (id >>> SnowflakeIdGenerator.TIMESTAMP_SHIFT) + SnowflakeIdGenerator.EPOCH_MILLIS;
        long workerId = (id >>> SnowflakeIdGenerator.WORKER_ID_SHIFT) & SnowflakeIdGenerator.MAX_WORKER_ID;
        long sequence = id & SnowflakeIdGenerator.MAX_SEQUENCE;
        return new SnowflakeId(timestamp, workerId, sequence);
    }

    public Instant instant() {
        return Instant.ofEpochMilli(timestamp);
    }
}
