package io.wulfcodes.messaging.chat.grpc;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverRequest;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverResponse;
import io.wulfcodes.messaging.chat.grpc.proto.NodeDeliveryGrpc;
import io.wulfcodes.messaging.chat.grpc.proto.PresenceQuery;
import io.wulfcodes.messaging.chat.grpc.proto.PresenceSnapshot;
import io.wulfcodes.messaging.chat.mapper.GrpcMessageMapper;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import io.wulfcodes.messaging.chat.service.spec.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC entry point (the gRPC equivalent of a controller) for calls from peer nodes:
 * pushes forwarded events to this node's local WebSocket sessions, and answers presence queries
 * for users this node owns. Registered on the gRPC server by Spring gRPC.
 */
@GrpcService
@RequiredArgsConstructor
public class NodeDeliveryGrpcService extends NodeDeliveryGrpc.NodeDeliveryImplBase {

    private final LocalDeliveryService localDeliveryService;
    private final SessionRegistry sessionRegistry;
    private final GrpcMessageMapper grpcMessageMapper;

    @Override
    public void deliver(DeliverRequest request, StreamObserver<DeliverResponse> responseObserver) {
        try {
            int delivered = localDeliveryService.deliverLocally(request.getUserId(),
                    grpcMessageMapper.toFrame(request),
                    GrpcMessageMapper.emptyToNull(request.getExcludeSessionId()));
            responseObserver.onNext(DeliverResponse.newBuilder().setDeliveredSessions(delivered).build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPresence(PresenceQuery request, StreamObserver<PresenceSnapshot> responseObserver) {
        PresenceSnapshot.Builder snapshot = PresenceSnapshot.newBuilder();
        request.getUserIdsList().stream()
                .filter(sessionRegistry::isConnected)
                .forEach(snapshot::addOnlineUserIds);
        responseObserver.onNext(snapshot.build());
        responseObserver.onCompleted();
    }
}
