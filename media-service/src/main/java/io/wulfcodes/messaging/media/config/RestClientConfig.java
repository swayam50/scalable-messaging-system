package io.wulfcodes.messaging.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** Base RestClient for chat-service calls; the node URL is chosen per call (failover across nodes). */
    @Bean
    public RestClient chatRestClient(RestClient.Builder builder) {
        return builder.build();
    }
}
