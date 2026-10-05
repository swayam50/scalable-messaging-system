package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.ClusterNode;
import io.wulfcodes.messaging.chat.model.po.eo.ClusterNodeKey;
import io.wulfcodes.messaging.chat.repository.custom.ClusterNodeCustomRepository;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface ClusterNodeRepository extends CassandraRepository<ClusterNode, ClusterNodeKey>, ClusterNodeCustomRepository {

    List<ClusterNode> findByKeyCluster(String cluster);
}
