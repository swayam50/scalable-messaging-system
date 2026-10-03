package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.exception.ClockMovedBackwardsException;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    private static final long T0 = SnowflakeIdGenerator.EPOCH_MILLIS + 1_000_000L;

    @Test
    void idsAreUniqueAcrossThreads() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(7);
        int threads = 8;
        int perThread = 125_000;
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        ids.add(generator.nextId());
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ids).hasSize(threads * perThread);
    }

    @Test
    void idsAreStrictlyIncreasingInOneThread() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);
        long previous = generator.nextId();
        for (int i = 0; i < 100_000; i++) {
            long next = generator.nextId();
            assertThat(next).isGreaterThan(previous);
            previous = next;
        }
    }

    @Test
    void decodeRecoversTimestampWorkerAndSequence() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42, new MutableClock(T0));

        SnowflakeId first = SnowflakeId.decode(generator.nextId());
        SnowflakeId second = SnowflakeId.decode(generator.nextId());

        assertThat(first).isEqualTo(new SnowflakeId(T0, 42, 0));
        assertThat(second).isEqualTo(new SnowflakeId(T0, 42, 1));
    }

    @Test
    void sequenceOverflowWaitsForNextMillisecond() {
        AtomicInteger calls = new AtomicInteger();
        MutableClock clock = new MutableClock(T0);
        // Stay on T0 for the first 4096 ids (plus the overflow check), then move to T0 + 1
        clock.script(() -> calls.incrementAndGet() <= 4097 ? T0 : T0 + 1);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, clock);

        for (int i = 0; i < 4096; i++) {
            SnowflakeId id = SnowflakeId.decode(generator.nextId());
            assertThat(id.timestamp()).isEqualTo(T0);
            assertThat(id.sequence()).isEqualTo(i);
        }
        SnowflakeId overflowed = SnowflakeId.decode(generator.nextId());

        assertThat(overflowed.timestamp()).isEqualTo(T0 + 1);
        assertThat(overflowed.sequence()).isZero();
    }

    @Test
    void clockMovingBackwardsIsRejected() {
        MutableClock clock = new MutableClock(T0);
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(5, clock);
        generator.nextId();

        clock.set(T0 - 5);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(ClockMovedBackwardsException.class)
                .hasMessageContaining("5 ms");
    }

    @Test
    void workerIdOutOfRangeIsRejected() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new SnowflakeIdGenerator(1023).workerId()).isEqualTo(1023);
    }
}
