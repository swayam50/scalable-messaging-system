package io.wulfcodes.messaging.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API version management (Spring Framework 7): version from path segment 1 (/api/v1/...),
 * applied only to /api/** so /ws/chat and /actuator are not parsed as versioned paths.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_V1 = "1";
    private static final String API_PREFIX = "/api/";

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(1, path -> path.pathWithinApplication().value().startsWith(API_PREFIX))
                .setVersionRequired(false)
                .addSupportedVersions(API_V1);
    }
}
