package io.wulfcodes.messaging.loadtest.util;

import io.wulfcodes.messaging.loadtest.config.LoadTestConfig;
import io.wulfcodes.messaging.loadtest.model.vo.StageResult;

import java.io.IOException;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

/** Writes the results as a markdown table (pasteable into the README). */
public final class ReportWriter {

    private ReportWriter() {
    }

    public static String markdown(LoadTestConfig config, List<StageResult> results, String environment) {
        StringBuilder md = new StringBuilder();
        md.append("## Load test results\n\n");
        md.append("- Run: ").append(ZonedDateTime.now().withNano(0)).append('\n');
        md.append("- Users / WebSockets: ").append(config.users()).append(" (").append(config.users() / 2)
                .append(" conversations, each user sends to its peer)\n");
        md.append("- Per stage: ").append(config.warmup().toSeconds()).append("s warm-up + ")
                .append(config.measure().toSeconds()).append("s measured, open-loop fixed send rate\n");
        md.append("- Environment: ").append(environment).append("\n\n");
        md.append("| Target msg/s | Sent/s | ACKed/s | Delivered/s | ACK p50 / p95 / p99 (ms) | Same-node delivery p50 / p99 | Cross-node (gRPC) delivery p50 / p99 | Errors | Timeouts |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (StageResult r : results) {
            md.append(String.format(Locale.ROOT, "| %d | %.0f | %.0f | %.0f | %.1f / %.1f / %.1f | %.1f / %.1f | %.1f / %.1f | %d | %d |%n",
                    r.targetRate(), r.achievedSendRate(), r.ackRate(), r.deliveredRate(),
                    r.ack().p50(), r.ack().p95(), r.ack().p99(),
                    r.deliverySameNode().p50(), r.deliverySameNode().p99(),
                    r.deliveryCrossNode().p50(), r.deliveryCrossNode().p99(),
                    r.errors(), r.timeouts()));
        }
        return md.toString();
    }

    public static void write(LoadTestConfig config, String markdown) throws IOException {
        Files.writeString(config.report(), markdown);
    }
}
