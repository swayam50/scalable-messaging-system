package io.wulfcodes.messaging.media.service.impl;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.common.util.AttachmentSigner;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import io.wulfcodes.messaging.media.config.MediaProperties;
import io.wulfcodes.messaging.media.exception.AttachmentNotFoundException;
import io.wulfcodes.messaging.media.exception.MediaTooLargeException;
import io.wulfcodes.messaging.media.exception.UnsupportedMediaException;
import io.wulfcodes.messaging.media.exception.UploadNotCompleteException;
import io.wulfcodes.messaging.media.mapper.AttachmentMapper;
import io.wulfcodes.messaging.media.model.dto.request.CreateUploadRequest;
import io.wulfcodes.messaging.media.model.dto.response.DownloadResponse;
import io.wulfcodes.messaging.media.model.dto.response.UploadTicketResponse;
import io.wulfcodes.messaging.media.model.po.Attachment;
import io.wulfcodes.messaging.media.model.vo.AttachmentStatus;
import io.wulfcodes.messaging.media.model.vo.MediaRule;
import io.wulfcodes.messaging.media.repository.AttachmentRepository;
import io.wulfcodes.messaging.media.service.spec.ConversationAccessService;
import io.wulfcodes.messaging.media.service.spec.MediaService;
import io.wulfcodes.messaging.media.service.spec.StorageService;
import io.wulfcodes.messaging.media.service.spec.StorageService.StoredObject;
import io.wulfcodes.messaging.media.util.MediaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaServiceImpl implements MediaService {

    private final AttachmentRepository attachmentRepository;
    private final StorageService storageService;
    private final ConversationAccessService conversationAccess;
    private final AttachmentMapper attachmentMapper;
    private final AttachmentSigner attachmentSigner;
    private final UlidGenerator ulidGenerator;
    private final MediaProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public UploadTicketResponse createUpload(String userId, String bearerToken, CreateUploadRequest request) {
        String mimeType = request.mimeType().toLowerCase(Locale.ROOT).trim();
        MediaRule rule = MediaPolicy.classify(mimeType, request.fileName())
                .orElseThrow(() -> new UnsupportedMediaException(mimeType));
        if (request.size() > rule.maxBytes()) {
            throw new MediaTooLargeException(rule.maxBytes());   // early, friendly check; storage enforces it again
        }
        conversationAccess.requireParticipant(request.conversationId(), bearerToken);

        String id = ulidGenerator.nextString();
        String fileName = MediaPolicy.sanitizeFileName(request.fileName());
        String objectKey = request.conversationId() + "/" + id + "/" + fileName;
        attachmentRepository.save(Attachment.builder()
                .id(id)
                .uploaderId(userId)
                .conversationId(request.conversationId())
                .contentType(rule.contentType())
                .fileName(fileName)
                .mimeType(mimeType)
                .sizeBytes(request.size())
                .objectKey(objectKey)
                .status(AttachmentStatus.PENDING)
                .build());

        Instant expiresAt = clock.instant().plus(properties.uploadUrlTtl());
        Map<String, String> form = storageService.presignUpload(objectKey, mimeType, rule.maxBytes(), expiresAt);
        return new UploadTicketResponse(id, rule.contentType(), storageService.uploadUrl(), form, rule.maxBytes(), expiresAt);
    }

    @Override
    @Transactional
    public AttachmentDescriptor completeUpload(String userId, String attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getUploaderId().equals(userId))
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));

        if (!attachment.isReady()) {
            // Trust what is actually in storage, not what the client declared.
            StoredObject stored = storageService.stat(attachment.getObjectKey())
                    .orElseThrow(() -> new UploadNotCompleteException("The file has not been uploaded yet"));
            if (!attachment.getMimeType().equalsIgnoreCase(stored.contentType())) {
                throw new UploadNotCompleteException("Stored file type does not match the upload ticket");
            }
            attachment.setSizeBytes(stored.size());
            attachment.setStatus(AttachmentStatus.READY);
        }
        return attachmentSigner.sign(attachmentMapper.toDescriptor(attachment));
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadResponse download(String attachmentId, String bearerToken) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(Attachment::isReady)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        conversationAccess.requireParticipant(attachment.getConversationId(), bearerToken);

        int ttl = (int) properties.downloadUrlTtl().toSeconds();
        boolean asDownload = attachment.getContentType() == ContentType.FILE;
        String url = storageService.presignDownload(attachment.getObjectKey(), attachment.getFileName(),
                attachment.getMimeType(), asDownload, ttl);
        return new DownloadResponse(attachment.getId(), attachment.getContentType(), attachment.getFileName(),
                attachment.getMimeType(), attachment.getSizeBytes(), url, clock.instant().plusSeconds(ttl));
    }
}
