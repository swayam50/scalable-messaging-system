package io.wulfcodes.messaging.chat.grpc;

import io.grpc.stub.StreamObserver;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverRequest;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverResponse;
import io.wulfcodes.messaging.chat.grpc.proto.NodeDeliveryGrpc;
import io.wulfcodes.messaging.chat.mapper.GrpcMessageMapper;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC entry point (the gRPC equivalent of a controller): receives messages forwarded by peer
 * nodes and pushes them to this node's local WebSocket sessions.
 * Spring gRPC registers it on the gRPC server automatically.
 */
@GrpcService
@RequiredArgsConstructor
public class NodeDeliveryGrpcService extends NodeDeliveryGrpc.NodeDeliveryImplBase {

    private final LocalDeliveryService localDeliveryService;
    private final GrpcMessageMapper grpcMessageMapper;

    @Override
    public void deliver(DeliverRequest request, StreamObserver<DeliverResponse> responseObserver) {
        ServerFrame frame = ServerFrame.message(grpcMessageMapper.fromProto(request.getMessage()));
        int delivered = localDeliveryService.deliverLocally(request.getUserId(), frame,
                GrpcMessageMapper.emptyToNull(request.getExcludeSessionId()));
        responseObserver.onNext(DeliverResponse.newBuilder().setDeliveredSessions(delivered).build());
        responseObserver.onCompleted();
    }
}
