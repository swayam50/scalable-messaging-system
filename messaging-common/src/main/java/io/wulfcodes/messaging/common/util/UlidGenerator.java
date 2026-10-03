package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.model.vo.Ulid;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Random;

/**
 * Monotonic ULID generator.
 * <p>
 * Within the same millisecond (or if the clock steps backwards) the previous ULID's random part
 * is incremented by one instead of drawing new randomness, so ids from one generator are
 * always strictly increasing.
 */
public final class UlidGenerator {

    private final Clock clock;
    private final Random random;

    private Ulid last;

    /**
     * @param random use {@link SecureRandom} in production: these ids are public, so they
     *               must not be predictable. Tests can pass a seeded {@link Random}.
     */
    public UlidGenerator(Clock clock, Random random) {
        this.clock = clock;
        this.random = random;
    }

    public UlidGenerator() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    public synchronized Ulid next() {
        long now = clock.millis();
        if (last != null && now <= last.timestamp()) {
            last = last.increment();
        } else {
            last = Ulid.of(now, random.nextInt(), random.nextLong());
        }
        return last;
    }

    public String nextString() {
        return next().toString();
    }
}
