package io.wulfcodes.messaging.loadtest.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed from {@code --key=value} arguments.
 *
 * @param chatUrls      chat node base URLs (REST); WebSocket URLs come from /api/v1/connect
 * @param users         number of virtual users (paired into users/2 conversations)
 * @param stageRates    target total send rates, messages/second, one stage each
 * @param warmup        per stage, not measured
 * @param measure       per stage, measured window
 * @param privateKey    PEM private key used to sign JWTs (public half is given to the chat nodes)
 */
public record LoadTestConfig(
        List<String> chatUrls,
        int users,
        List<Integer> stageRates,
        Duration warmup,
        Duration measure,
        Path privateKey,
        String issuer,
        String origin,
        Path report
) {

    public static LoadTestConfig parse(String[] args) {
        Map<String, String> a = new HashMap<>();
        for (String arg : args) {
            if (arg.startsWith("--") && arg.contains("=")) {
                a.put(arg.substring(2, arg.indexOf('=')), arg.substring(arg.indexOf('=') + 1));
            }
        }
        return new LoadTestConfig(
                List.of(a.getOrDefault("chat-urls", "http://localhost:8082").split(",")),
                Integer.parseInt(a.getOrDefault("users", "1000")),
                Arrays.stream(a.getOrDefault("rates", "500,1000,2000,4000").split(",")).map(Integer::parseInt).toList(),
                Duration.ofSeconds(Long.parseLong(a.getOrDefault("warmup-seconds", "5"))),
                Duration.ofSeconds(Long.parseLong(a.getOrDefault("measure-seconds", "20"))),
                Path.of(a.getOrDefault("private-key", "load-test-keys/private.pem")),
                a.getOrDefault("issuer", "load-test"),
                a.getOrDefault("origin", "http://localhost:8080"),
                Path.of(a.getOrDefault("report", "load-test-results.md")));
    }
}
