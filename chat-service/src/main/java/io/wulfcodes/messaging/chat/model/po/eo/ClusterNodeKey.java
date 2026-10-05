package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * Primary key (cluster, node_id): all nodes of a cluster live in one small partition,
 * so reading the full membership is a single-partition query.
 */
@PrimaryKeyClass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ClusterNodeKey {

    @PrimaryKeyColumn(name = "cluster", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String cluster;

    @PrimaryKeyColumn(name = "node_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private String nodeId;
}
