package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.UserPresence;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface UserPresenceRepository extends CassandraRepository<UserPresence, String> {
}
