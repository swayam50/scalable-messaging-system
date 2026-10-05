package io.wulfcodes.messaging.chat.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.wulfcodes.messaging.chat.config.ChatProperties;

/**
 * Adds the shared cluster secret to every outgoing node-to-node gRPC call.
 */
public class ClusterTokenClientInterceptor implements ClientInterceptor {

    private final String token;

    public ClusterTokenClientInterceptor(ChatProperties properties) {
        this.token = properties.cluster().secret();
    }

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<R> listener, Metadata headers) {
                headers.put(ClusterTokenServerInterceptor.CLUSTER_TOKEN, token);
                super.start(listener, headers);
            }
        };
    }
}
