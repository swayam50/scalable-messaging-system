package io.wulfcodes.messaging.loadtest.service.impl;

import io.wulfcodes.messaging.loadtest.service.spec.ChatApiClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** REST calls spread round-robin over the chat nodes (any node can answer). */
public class HttpChatApiClient implements ChatApiClient {

    private final HttpClient http;
    private final List<String> baseUrls;
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger next = new AtomicInteger();

    public HttpChatApiClient(HttpClient http, List<String> baseUrls) {
        this.http = http;
        this.baseUrls = baseUrls;
    }

    @Override
    public String createConversation(String token, String peerId) {
        HttpRequest request = request("/api/v1/conversations", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"peerId\":\"" + peerId + "\"}"))
                .build();
        return send(request).get("conversationId").asString();
    }

    @Override
    public String[] connect(String token) {
        JsonNode owner = send(request("/api/v1/connect", token).GET().build());
        return new String[]{owner.get("nodeId").asString(), owner.get("wsUrl").asString()};
    }

    private HttpRequest.Builder request(String path, String token) {
        String base = baseUrls.get(Math.floorMod(next.getAndIncrement(), baseUrls.size()));
        return HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new IllegalStateException(request.uri() + " -> " + response.statusCode() + " " + response.body());
            }
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(request.uri() + " failed: " + e.getMessage(), e);
        }
    }
}
