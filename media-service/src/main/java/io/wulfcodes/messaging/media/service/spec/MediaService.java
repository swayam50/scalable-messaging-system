package io.wulfcodes.messaging.media.service.spec;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.media.model.dto.request.CreateUploadRequest;
import io.wulfcodes.messaging.media.model.dto.response.DownloadResponse;
import io.wulfcodes.messaging.media.model.dto.response.UploadTicketResponse;

public interface MediaService {

    /** Step 1: validate type/size/membership, return a presigned upload form. */
    UploadTicketResponse createUpload(String userId, String bearerToken, CreateUploadRequest request);

    /** Step 2 (after the browser uploaded): verify the object and return a SIGNED descriptor to send in chat. */
    AttachmentDescriptor completeUpload(String userId, String attachmentId);

    /** Presigned download URL, for conversation participants only. */
    DownloadResponse download(String attachmentId, String bearerToken);
}
