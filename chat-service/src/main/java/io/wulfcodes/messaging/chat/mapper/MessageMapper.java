package io.wulfcodes.messaging.chat.mapper;

import io.wulfcodes.messaging.chat.model.dto.response.InboxEntryResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import io.wulfcodes.messaging.chat.model.po.Message;
import io.wulfcodes.messaging.common.model.vo.SnowflakeId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Instant;
import java.util.List;

/**
 * po -> dto. The send time is not stored separately: it is decoded from the Snowflake id.
 */
@Mapper
public interface MessageMapper {

    @Mapping(target = "messageId", source = "key.messageId", qualifiedByName = "idToString")
    @Mapping(target = "conversationId", source = "key.conversationId")
    @Mapping(target = "sentAt", source = "key.messageId", qualifiedByName = "idToInstant")
    MessageResponse toResponse(Message message);

    List<MessageResponse> toResponses(List<Message> messages);

    @Mapping(target = "conversationId", source = "key.conversationId")
    @Mapping(target = "lastMessageId", source = "lastMessageId", qualifiedByName = "nullableIdToString")
    @Mapping(target = "lastMessageAt", source = "lastMessageId", qualifiedByName = "nullableIdToInstant")
    InboxEntryResponse toInboxResponse(InboxEntry entry);

    @Named("idToString")
    default String idToString(long id) {
        return Long.toString(id);
    }

    @Named("idToInstant")
    default Instant idToInstant(long id) {
        return SnowflakeId.decode(id).instant();
    }

    @Named("nullableIdToString")
    default String nullableIdToString(Long id) {
        return id == null ? null : Long.toString(id);
    }

    @Named("nullableIdToInstant")
    default Instant nullableIdToInstant(Long id) {
        return id == null ? null : SnowflakeId.decode(id).instant();
    }
}
