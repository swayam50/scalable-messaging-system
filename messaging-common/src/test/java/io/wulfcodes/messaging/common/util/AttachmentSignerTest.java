package io.wulfcodes.messaging.common.util;

import io.wulfcodes.messaging.common.model.dto.AttachmentDescriptor;
import io.wulfcodes.messaging.common.model.vo.ContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentSignerTest {

    private static final String SECRET = "a-test-secret-that-is-long-enough-1234";
    private final AttachmentSigner signer = new AttachmentSigner(SECRET);
    private final AttachmentDescriptor descriptor = new AttachmentDescriptor(
            "ATT", "CONV", "ALICE", ContentType.IMAGE, "cat.png", "image/png", 2048, null);

    @Test
    void signedDescriptorVerifies() {
        assertThat(signer.isValid(signer.sign(descriptor))).isTrue();
    }

    @Test
    void anyTamperingInvalidatesTheSignature() {
        AttachmentDescriptor signed = signer.sign(descriptor);
        String sig = signed.signature();

        assertThat(signer.isValid(new AttachmentDescriptor("ATT", "CONV", "MALLORY", ContentType.IMAGE, "cat.png", "image/png", 2048, sig))).isFalse();
        assertThat(signer.isValid(new AttachmentDescriptor("ATT", "OTHER", "ALICE", ContentType.IMAGE, "cat.png", "image/png", 2048, sig))).isFalse();
        assertThat(signer.isValid(new AttachmentDescriptor("ATT", "CONV", "ALICE", ContentType.VIDEO, "cat.png", "image/png", 2048, sig))).isFalse();
        assertThat(signer.isValid(new AttachmentDescriptor("ATT", "CONV", "ALICE", ContentType.IMAGE, "cat.png", "image/png", 1, sig))).isFalse();
    }

    @Test
    void otherSecretCannotForge() {
        AttachmentDescriptor forged = new AttachmentSigner("another-secret-that-is-long-enough-5678").sign(descriptor);
        assertThat(signer.isValid(forged)).isFalse();
    }

    @Test
    void missingSignatureAndShortSecretAreRejected() {
        assertThat(signer.isValid(descriptor)).isFalse();
        assertThatThrownBy(() -> new AttachmentSigner("short")).isInstanceOf(IllegalArgumentException.class);
    }
}
