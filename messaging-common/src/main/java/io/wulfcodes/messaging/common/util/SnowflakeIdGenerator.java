package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.exception.ClockMovedBackwardsException;

import java.time.Clock;
import java.time.Instant;

/**
 * Twitter-style Snowflake id generator.
 *
 * <pre>
 *  0 | 41 bits: ms since EPOCH | 10 bits: workerId | 12 bits: sequence
 * </pre>
 *
 * <ul>
 *   <li>41 bits of milliseconds cover ~69 years from {@link #EPOCH_MILLIS}.</li>
 *   <li>10 bits of worker id allow 1024 generator nodes.</li>
 *   <li>12 bits of sequence allow 4096 ids per millisecond per node.</li>
 * </ul>
 *
 * Ids are unique without coordination (given unique worker ids) and sort by creation time,
 * which makes them good clustering keys in ScyllaDB.
 */
public final class SnowflakeIdGenerator implements IdGenerator {

    /** Custom epoch 2026-01-01T00:00:00Z: starting later than 1970 extends the usable range. */
    public static final long EPOCH_MILLIS = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();

    static final int WORKER_ID_BITS = 10;
    static final int SEQUENCE_BITS = 12;

    public static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;   // 1023
    public static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;     // 4095

    public static final int WORKER_ID_SHIFT = SEQUENCE_BITS;                 // 12
    public static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS; // 22

    private final long workerId;
    private final Clock clock;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId, Clock clock) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID + ", was " + workerId);
        }
        this.workerId = workerId;
        this.clock = clock;
    }

    public SnowflakeIdGenerator(long workerId) {
        this(workerId, Clock.systemUTC());
    }

    /**
     * {@code synchronized} keeps (lastTimestamp, sequence) consistent across threads.
     * It is simple, correct and already fast enough for millions of ids per second;
     * a lock-free CAS version would only be worth it after measuring contention.
     */
    @Override
    public synchronized long nextId() {
        long now = clock.millis();

        if (now < lastTimestamp) {
            throw new ClockMovedBackwardsException(lastTimestamp, now);
        }

        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // 4096 ids already issued in this millisecond: wait for the next one
                now = waitUntilAfter(lastTimestamp);
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = now;
        return ((now - EPOCH_MILLIS) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long waitUntilAfter(long timestamp) {
        long now = clock.millis();
        while (now <= timestamp) {
            Thread.onSpinWait();
            now = clock.millis();
        }
        return now;
    }

    public long workerId() {
        return workerId;
    }
}
