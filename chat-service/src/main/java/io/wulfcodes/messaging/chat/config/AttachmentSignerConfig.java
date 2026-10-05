package io.wulfcodes.messaging.chat.config;

import io.wulfcodes.messaging.common.util.AttachmentSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttachmentSignerConfig {

    /** Verifies attachment descriptors signed by media-service (shared HMAC secret). */
    @Bean
    public AttachmentSigner attachmentSigner(ChatProperties properties) {
        return new AttachmentSigner(properties.media().signingSecret());
    }
}
