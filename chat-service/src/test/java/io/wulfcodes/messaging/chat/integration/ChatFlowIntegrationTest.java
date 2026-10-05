package io.wulfcodes.messaging.chat.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.scylladb.ScyllaDBContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Full stack against a real ScyllaDB (Testcontainers): REST + two real WebSocket clients.
 * The JwtDecoder is mocked so the test does not need a running auth-service:
 * "token-alice" / "token-bob" decode to those users, anything else is rejected.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatFlowIntegrationTest {

    @Container
    static final ScyllaDBContainer SCYLLA = new ScyllaDBContainer("scylladb/scylla:2026.3.2")
            .withCommand("--smp 1 --memory 1G --overprovisioned 1 --developer-mode 1");

    @DynamicPropertySource
    static void scylla(DynamicPropertyRegistry registry) {
        registry.add("spring.cassandra.contact-points",
                () -> SCYLLA.getContactPoint().getHostString() + ":" + SCYLLA.getContactPoint().getPort());
        registry.add("spring.cassandra.local-datacenter", () -> "datacenter1");
        registry.add("spring.cassandra.request.timeout", () -> "15s");
        registry.add("spring.grpc.server.port", () -> "0");   // single node: no peers to call
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @LocalServerPort
    private int port;

    private RestClient rest;

    @BeforeEach
    void setUp() {
        when(jwtDecoder.decode(anyString())).thenAnswer(inv -> {
            String token = inv.getArgument(0);
            if (!token.startsWith("token-")) {
                throw new BadJwtException("bad token");
            }
            String user = token.substring("token-".length()).toUpperCase();
            return Jwt.withTokenValue(token).header("alg", "RS256").subject(user)
                    .claim("username", user.toLowerCase())
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void messagesFlowOverWebSocketAndLandInHistoryAndInbox() throws Exception {
        String conversationId = createConversation("token-alice", "BOB");
        // idempotent: asking again (from the other side) returns the same conversation
        assertThat(createConversation("token-bob", "ALICE")).isEqualTo(conversationId);

        BlockingQueue<String> aliceFrames = new LinkedBlockingQueue<>();
        BlockingQueue<String> bobFrames = new LinkedBlockingQueue<>();
        WebSocket alice = connect("token-alice", aliceFrames);
        connect("token-bob", bobFrames);

        alice.sendText("""
                {"type":"SEND","conversationId":"%s","clientMessageId":"c-1","body":"Hi Bob"}""".formatted(conversationId), true);

        String ack = aliceFrames.poll(10, TimeUnit.SECONDS);
        String delivered = bobFrames.poll(10, TimeUnit.SECONDS);
        assertThat((String) JsonPath.read(ack, "$.type")).isEqualTo("ACK");
        assertThat((String) JsonPath.read(ack, "$.clientMessageId")).isEqualTo("c-1");
        assertThat((String) JsonPath.read(delivered, "$.type")).isEqualTo("MESSAGE");
        assertThat((String) JsonPath.read(delivered, "$.message.body")).isEqualTo("Hi Bob");
        String messageId = JsonPath.read(ack, "$.message.messageId");
        assertThat((String) JsonPath.read(delivered, "$.message.messageId")).isEqualTo(messageId);

        // persisted in history...
        String history = rest.get().uri("/api/v1/conversations/{id}/messages", conversationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-bob").retrieve().body(String.class);
        List<String> ids = JsonPath.read(history, "$.messages[*].messageId");
        assertThat(ids).containsExactly(messageId);

        // ...and both inboxes show it as the last message
        String inbox = rest.get().uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-bob").retrieve().body(String.class);
        assertThat((String) JsonPath.read(inbox, "$[0].preview")).isEqualTo("Hi Bob");
        assertThat((String) JsonPath.read(inbox, "$[0].lastMessageId")).isEqualTo(messageId);
    }

    @Test
    void outsiderCannotSendIntoSomeoneElsesConversation() throws Exception {
        String conversationId = createConversation("token-alice", "BOB");
        BlockingQueue<String> malloryFrames = new LinkedBlockingQueue<>();
        WebSocket mallory = connect("token-mallory", malloryFrames);

        mallory.sendText("""
                {"type":"SEND","conversationId":"%s","clientMessageId":"x","body":"sneaky"}""".formatted(conversationId), true);

        String reply = malloryFrames.poll(10, TimeUnit.SECONDS);
        assertThat((String) JsonPath.read(reply, "$.type")).isEqualTo("ERROR");
    }

    @Test
    void handshakeWithInvalidTokenIsRefused() {
        assertThatThrownBy(() -> connect("garbage", new LinkedBlockingQueue<>()))
                .isInstanceOf(CompletionException.class);
    }

    private String createConversation(String token, String peerId) {
        String body = rest.post().uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"peerId\":\"" + peerId + "\"}")
                .retrieve().body(String.class);
        return JsonPath.read(body, "$.conversationId");
    }

    private WebSocket connect(String token, BlockingQueue<String> frames) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "http://localhost:8080")
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/chat?access_token=" + token),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                                frames.add(data.toString());
                                ws.request(1);
                                return null;
                            }
                        })
                .join();
    }
}
