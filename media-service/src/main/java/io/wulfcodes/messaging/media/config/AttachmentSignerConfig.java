package io.wulfcodes.messaging.media.config;

import io.wulfcodes.messaging.common.util.AttachmentSigner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AttachmentSignerConfig {

    @Bean
    public AttachmentSigner attachmentSigner(MediaProperties properties) {
        return new AttachmentSigner(properties.signingSecret());
    }
}
