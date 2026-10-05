package io.wulfcodes.messaging.media.mapper;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.media.model.po.Attachment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** po -> shared descriptor dto (signed afterwards by AttachmentSigner). */
@Mapper
public interface AttachmentMapper {

    @Mapping(target = "attachmentId", source = "id")
    @Mapping(target = "size", source = "sizeBytes")
    @Mapping(target = "signature", ignore = true)
    AttachmentDescriptor toDescriptor(Attachment attachment);
}
