package io.wulfcodes.messaging.chat.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.wulfcodes.messaging.chat.config.ChatProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Authenticates node-to-node gRPC calls with a shared cluster secret (x-cluster-token header).
 * Only chat nodes know the secret, so nobody else on the network can inject messages.
 * MessageDigest.isEqual compares in constant time (no timing leak of the secret).
 * Production upgrade: mutual TLS between nodes.
 */
public class ClusterTokenServerInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> CLUSTER_TOKEN =
            Metadata.Key.of("x-cluster-token", Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expected;

    public ClusterTokenServerInterceptor(ChatProperties properties) {
        this.expected = properties.cluster().secret().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                       ServerCallHandler<Q, R> next) {
        String presented = headers.get(CLUSTER_TOKEN);
        if (presented == null || !MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("invalid cluster token"), new Metadata());
            return new ServerCall.Listener<>() {
            };
        }
        return next.startCall(call, headers);
    }
}
