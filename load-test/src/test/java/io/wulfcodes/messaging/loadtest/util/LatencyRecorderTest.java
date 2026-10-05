package io.wulfcodes.messaging.loadtest.util;

import io.wulfcodes.messaging.loadtest.model.vo.StageResult.Percentiles;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyRecorderTest {

    @Test
    void nearestRankPercentilesInMilliseconds() {
        LatencyRecorder recorder = new LatencyRecorder(1_000);
        for (int ms = 1; ms <= 100; ms++) {
            recorder.record(ms * 1_000_000L);
        }

        Percentiles p = recorder.percentiles();

        assertThat(p.count()).isEqualTo(100);
        assertThat(p.p50()).isEqualTo(50.0);
        assertThat(p.p95()).isEqualTo(95.0);
        assertThat(p.p99()).isEqualTo(99.0);
        assertThat(p.max()).isEqualTo(100.0);
    }

    @Test
    void emptyAndOverflowAreSafe() {
        assertThat(new LatencyRecorder(10).percentiles().count()).isZero();

        LatencyRecorder small = new LatencyRecorder(2);
        small.record(1);
        small.record(2);
        small.record(3);   // beyond capacity: ignored, no exception
        assertThat(small.percentiles().count()).isEqualTo(2);
    }
}
