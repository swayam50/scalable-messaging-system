package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Signs and verifies {@link AttachmentDescriptor}s with HMAC-SHA256 and a secret shared by
 * media-service (signs) and chat-service (verifies).
 * <p>
 * This lets chat-service accept "I attached file X" from a client without a network call to
 * media-service: only media-service can produce a valid signature, and it only does so after it has
 * checked the uploaded object, so a client cannot forge or alter an attachment (other uploader,
 * other conversation, other type, fake size...).
 */
public final class AttachmentSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public AttachmentSigner(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Attachment signing secret must be at least 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public AttachmentDescriptor sign(AttachmentDescriptor descriptor) {
        AttachmentDescriptor d = descriptor;
        return new AttachmentDescriptor(d.attachmentId(), d.conversationId(), d.uploaderId(), d.contentType(),
                d.fileName(), d.mimeType(), d.size(), signatureOf(d));
    }

    /** Constant-time comparison, so response timing reveals nothing about the expected signature. */
    public boolean isValid(AttachmentDescriptor descriptor) {
        if (descriptor == null || descriptor.signature() == null) {
            return false;
        }
        byte[] expected = signatureOf(descriptor).getBytes(StandardCharsets.UTF_8);
        byte[] presented = descriptor.signature().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, presented);
    }

    private String signatureOf(AttachmentDescriptor d) {
        // Length-prefixed fields: "ab"+"c" and "a"+"bc" can never produce the same input.
        String canonical = String.join("|",
                field(d.attachmentId()), field(d.conversationId()), field(d.uploaderId()),
                field(String.valueOf(d.contentType())), field(d.fileName()), field(d.mimeType()),
                field(Long.toString(d.size())));
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static String field(String value) {
        String v = value == null ? "" : value;
        return v.length() + ":" + v;
    }
}
