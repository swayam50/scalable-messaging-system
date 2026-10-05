package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

/**
 * ScyllaDB user-defined type embedded in a message row: persisted, but no table of its own.
 */
@UserDefinedType("attachment")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentUdt {

    @Column("attachment_id")
    private String attachmentId;

    @Column("file_name")
    private String fileName;

    @Column("mime_type")
    private String mimeType;

    @Column("size")
    private long size;
}
