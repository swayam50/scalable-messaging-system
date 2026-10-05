package io.wulfcodes.messaging.media.controller.resource.v1;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.media.config.WebConfig;
import io.wulfcodes.messaging.media.model.dto.request.CreateUploadRequest;
import io.wulfcodes.messaging.media.model.dto.response.DownloadResponse;
import io.wulfcodes.messaging.media.model.dto.response.UploadTicketResponse;
import io.wulfcodes.messaging.media.service.spec.MediaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Media API v1. Upload flow:
 * <pre>
 *  1. POST /api/v1/media/uploads             -> presigned POST form (browser uploads to storage)
 *  2. POST /api/v1/media/{id}/complete       -> verified + signed descriptor (sent inside the chat frame)
 *  3. GET  /api/v1/media/{id}/download       -> short-lived presigned URL (participants only)
 * </pre>
 */
@RestController
@RequestMapping("/api/{version}/media")
@RequiredArgsConstructor
public class MediaResource {

    private final MediaService mediaService;

    @PostMapping(path = "/uploads", version = WebConfig.API_V1)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadTicketResponse createUpload(@AuthenticationPrincipal Jwt jwt,
                                             @Valid @RequestBody CreateUploadRequest request) {
        return mediaService.createUpload(jwt.getSubject(), jwt.getTokenValue(), request);
    }

    @PostMapping(path = "/{attachmentId}/complete", version = WebConfig.API_V1)
    public AttachmentDescriptor complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String attachmentId) {
        return mediaService.completeUpload(jwt.getSubject(), attachmentId);
    }

    @GetMapping(path = "/{attachmentId}/download", version = WebConfig.API_V1)
    public DownloadResponse download(@AuthenticationPrincipal Jwt jwt, @PathVariable String attachmentId) {
        return mediaService.download(attachmentId, jwt.getTokenValue());
    }
}
