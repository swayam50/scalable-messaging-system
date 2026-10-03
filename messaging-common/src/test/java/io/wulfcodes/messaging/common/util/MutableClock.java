package io.wulfcodes.messaging.common.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.function.LongSupplier;

/**
 * Test clock whose time is controlled by the test (or by a supplier for scripted sequences).
 */
final class MutableClock extends Clock {

    private LongSupplier millis;

    MutableClock(long startMillis) {
        set(startMillis);
    }

    void set(long millis) {
        this.millis = () -> millis;
    }

    void script(LongSupplier supplier) {
        this.millis = supplier;
    }

    @Override
    public long millis() {
        return millis.getAsLong();
    }

    @Override
    public Instant instant() {
        return Instant.ofEpochMilli(millis());
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
