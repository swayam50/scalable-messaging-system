package io.wulfcodes.messaging.loadtest.util;

import io.wulfcodes.messaging.loadtest.model.vo.StageResult.Percentiles;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lock-free latency recorder: samples (in nanoseconds) go into a pre-sized array, and percentiles
 * are computed by sorting once at the end of a stage. Exact percentiles, no histogram buckets.
 */
public final class LatencyRecorder {

    private final long[] samples;
    private final AtomicInteger size = new AtomicInteger();

    public LatencyRecorder(int capacity) {
        this.samples = new long[capacity];
    }

    public void record(long nanos) {
        int i = size.getAndIncrement();
        if (i < samples.length) {
            samples[i] = nanos;
        }
    }

    public Percentiles percentiles() {
        int n = Math.min(size.get(), samples.length);
        if (n == 0) {
            return new Percentiles(0, 0, 0, 0, 0);
        }
        long[] sorted = Arrays.copyOf(samples, n);
        Arrays.sort(sorted);
        return new Percentiles(n, ms(at(sorted, 0.50)), ms(at(sorted, 0.95)), ms(at(sorted, 0.99)), ms(sorted[n - 1]));
    }

    /** Nearest-rank percentile. */
    static long at(long[] sorted, double quantile) {
        int rank = (int) Math.ceil(quantile * sorted.length);
        return sorted[Math.clamp(rank - 1, 0, sorted.length - 1)];
    }

    private static double ms(long nanos) {
        return nanos / 1_000_000.0;
    }
}
