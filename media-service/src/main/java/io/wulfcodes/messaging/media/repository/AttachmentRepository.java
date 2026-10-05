package io.wulfcodes.messaging.media.repository;

import io.wulfcodes.messaging.media.model.po.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, String> {
}
