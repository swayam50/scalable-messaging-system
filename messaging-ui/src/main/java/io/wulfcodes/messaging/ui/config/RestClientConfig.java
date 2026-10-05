package io.wulfcodes.messaging.ui.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /** RestClient pre-configured with auth-service's base URL (Boot's builder adds JSON support). */
    @Bean
    public RestClient authRestClient(RestClient.Builder builder, UiProperties properties) {
        return builder.baseUrl(properties.authServiceUrl()).build();
    }
}
