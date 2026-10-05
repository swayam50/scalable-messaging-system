package io.wulfcodes.messaging.media.service.impl;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.common.util.AttachmentSigner;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import io.wulfcodes.messaging.media.config.MediaProperties;
import io.wulfcodes.messaging.media.exception.AttachmentNotFoundException;
import io.wulfcodes.messaging.media.exception.ConversationAccessDeniedException;
import io.wulfcodes.messaging.media.exception.MediaTooLargeException;
import io.wulfcodes.messaging.media.exception.UnsupportedMediaException;
import io.wulfcodes.messaging.media.exception.UploadNotCompleteException;
import io.wulfcodes.messaging.media.mapper.AttachmentMapperImpl;
import io.wulfcodes.messaging.media.model.dto.request.CreateUploadRequest;
import io.wulfcodes.messaging.media.model.dto.response.UploadTicketResponse;
import io.wulfcodes.messaging.media.model.po.Attachment;
import io.wulfcodes.messaging.media.model.vo.AttachmentStatus;
import io.wulfcodes.messaging.media.repository.AttachmentRepository;
import io.wulfcodes.messaging.media.service.spec.ConversationAccessService;
import io.wulfcodes.messaging.media.service.spec.StorageService;
import io.wulfcodes.messaging.media.service.spec.StorageService.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceImplTest {

    private static final AttachmentSigner SIGNER = new AttachmentSigner("test-signing-secret-that-is-long-enough");
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private StorageService storageService;
    @Mock
    private ConversationAccessService conversationAccess;

    private MediaServiceImpl service;

    @BeforeEach
    void setUp() {
        MediaProperties properties = new MediaProperties(null, Duration.ofMinutes(10), Duration.ofMinutes(10),
                "test-signing-secret-that-is-long-enough", List.of(), null, null);
        service = new MediaServiceImpl(attachmentRepository, storageService, conversationAccess, new AttachmentMapperImpl(),
                SIGNER, new UlidGenerator(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uploadTicketIsScopedToConversationAndSizeLimit() {
        when(storageService.presignUpload(anyString(), eq("image/png"), eq(10L * 1024 * 1024), any()))
                .thenReturn(Map.of("policy", "p"));
        when(storageService.uploadUrl()).thenReturn("http://storage/chat-media");

        UploadTicketResponse ticket = service.createUpload("ALICE", "token",
                new CreateUploadRequest("CONV", "My Cat.png", "image/png", 2048));

        ArgumentCaptor<Attachment> saved = ArgumentCaptor.forClass(Attachment.class);
        verify(attachmentRepository).save(saved.capture());
        assertThat(saved.getValue().getObjectKey()).isEqualTo("CONV/" + ticket.attachmentId() + "/My_Cat.png");
        assertThat(saved.getValue().getStatus()).isEqualTo(AttachmentStatus.PENDING);
        assertThat(ticket.contentType()).isEqualTo(ContentType.IMAGE);
        verify(conversationAccess).requireParticipant("CONV", "token");
    }

    @Test
    void unsupportedOrTooLargeOrForeignConversationIsRejectedBeforeAnythingIsStored() {
        assertThatThrownBy(() -> service.createUpload("A", "t", new CreateUploadRequest("C", "x.exe", "application/x-msdownload", 10)))
                .isInstanceOf(UnsupportedMediaException.class);
        assertThatThrownBy(() -> service.createUpload("A", "t", new CreateUploadRequest("C", "big.png", "image/png", 11L * 1024 * 1024)))
                .isInstanceOf(MediaTooLargeException.class);
        doThrow(new ConversationAccessDeniedException("C")).when(conversationAccess).requireParticipant("C", "t");
        assertThatThrownBy(() -> service.createUpload("A", "t", new CreateUploadRequest("C", "ok.png", "image/png", 10)))
                .isInstanceOf(ConversationAccessDeniedException.class);
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void completeTrustsStorageNotTheClientAndReturnsASignedDescriptor() {
        Attachment pending = pending("ALICE");
        when(attachmentRepository.findById("ATT")).thenReturn(Optional.of(pending));
        when(storageService.stat(pending.getObjectKey())).thenReturn(Optional.of(new StoredObject(4321, "image/png")));

        AttachmentDescriptor descriptor = service.completeUpload("ALICE", "ATT");

        assertThat(descriptor.size()).isEqualTo(4321);              // real size, not the declared 2048
        assertThat(SIGNER.isValid(descriptor)).isTrue();
        assertThat(pending.getStatus()).isEqualTo(AttachmentStatus.READY);
    }

    @Test
    void completeFailsIfNothingWasUploadedOrTypeDiffersOrCallerIsNotTheUploader() {
        when(attachmentRepository.findById("ATT")).thenReturn(Optional.of(pending("ALICE")));
        when(storageService.stat(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeUpload("ALICE", "ATT")).isInstanceOf(UploadNotCompleteException.class);

        when(storageService.stat(anyString())).thenReturn(Optional.of(new StoredObject(10, "text/html")));
        assertThatThrownBy(() -> service.completeUpload("ALICE", "ATT")).isInstanceOf(UploadNotCompleteException.class);

        assertThatThrownBy(() -> service.completeUpload("MALLORY", "ATT")).isInstanceOf(AttachmentNotFoundException.class);
    }

    @Test
    void downloadRequiresConversationMembershipAndForcesSaveAsForPlainFiles() {
        Attachment file = pending("ALICE");
        file.setStatus(AttachmentStatus.READY);
        file.setContentType(ContentType.FILE);
        when(attachmentRepository.findById("ATT")).thenReturn(Optional.of(file));
        when(storageService.presignDownload(anyString(), anyString(), anyString(), eq(true), eq(600)))
                .thenReturn("http://storage/signed");

        assertThat(service.download("ATT", "token").url()).isEqualTo("http://storage/signed");
        verify(conversationAccess).requireParticipant("CONV", "token");
    }

    private static Attachment pending(String uploader) {
        return Attachment.builder().id("ATT").uploaderId(uploader).conversationId("CONV")
                .contentType(ContentType.IMAGE).fileName("cat.png").mimeType("image/png").sizeBytes(2048)
                .objectKey("CONV/ATT/cat.png").status(AttachmentStatus.PENDING).build();
    }
}
