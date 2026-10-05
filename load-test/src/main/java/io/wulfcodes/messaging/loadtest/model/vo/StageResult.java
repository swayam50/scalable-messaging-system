package io.wulfcodes.messaging.loadtest.model.vo;

/**
 * Measurements of one load stage (latencies in milliseconds).
 */
public record StageResult(
        int targetRate,
        double achievedSendRate,
        double ackRate,
        double deliveredRate,
        long errors,
        long timeouts,
        Percentiles ack,
        Percentiles deliverySameNode,
        Percentiles deliveryCrossNode
) {

    public record Percentiles(long count, double p50, double p95, double p99, double max) {
    }
}
