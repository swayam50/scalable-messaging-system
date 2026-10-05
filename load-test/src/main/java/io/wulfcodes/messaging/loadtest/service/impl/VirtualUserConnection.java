package io.wulfcodes.messaging.loadtest.service.impl;

import io.wulfcodes.messaging.loadtest.model.vo.VirtualUserSpec;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * One virtual user's WebSocket. The JDK WebSocket allows only one outstanding send at a time,
 * so sends are chained: each waits for the previous one to complete.
 */
public class VirtualUserConnection {

    private final VirtualUserSpec spec;
    private final WebSocket socket;
    private CompletableFuture<WebSocket> tail;

    private VirtualUserConnection(VirtualUserSpec spec, WebSocket socket) {
        this.spec = spec;
        this.socket = socket;
        this.tail = CompletableFuture.completedFuture(socket);
    }

    public static CompletableFuture<VirtualUserConnection> open(HttpClient http, VirtualUserSpec spec, String token,
                                                                String origin, Consumer<String> onFrame) {
        StringBuilder partial = new StringBuilder();
        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    onFrame.accept(partial.toString());
                    partial.setLength(0);
                }
                ws.request(1);
                return null;
            }
        };
        return http.newWebSocketBuilder()
                .header("Origin", origin)
                .buildAsync(URI.create(spec.wsUrl() + "?access_token=" + token), listener)
                .thenApply(ws -> new VirtualUserConnection(spec, ws));
    }

    /** @param onError called if the send fails (connection broken) */
    public synchronized void send(String frame, Runnable onError) {
        tail = tail.thenCompose(ws -> ws.sendText(frame, true))
                .exceptionally(e -> {
                    onError.run();
                    return socket;
                });
    }

    public VirtualUserSpec spec() {
        return spec;
    }

    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }
}
