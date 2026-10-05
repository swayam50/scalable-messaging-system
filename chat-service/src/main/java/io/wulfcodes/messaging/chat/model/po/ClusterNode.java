package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.ClusterNodeKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * Membership row of a live chat-service node (written with a TTL as a heartbeat).
 */
@Table("cluster_nodes")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNode {

    @PrimaryKey
    private ClusterNodeKey key;

    @Column("grpc_address")
    private String grpcAddress;

    @Column("ws_url")
    private String wsUrl;
}
