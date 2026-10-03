package io.wulfcodes.messaging.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API version management (Spring Framework 7 built-in versioning).
 * <p>
 * The version is read from path segment 1: /api/{version}/... e.g. /api/v1/auth/login.
 * Each mapping declares the version it serves ({@code @PostMapping(path = "/login", version = "1")}),
 * and requests for an unsupported version are rejected with 400.
 * <p>
 * Versioning is applied only to /api/** paths. Without the predicate, standard unversioned
 * endpoints like /.well-known/jwks.json or /actuator/health would have "jwks.json" or
 * "health" parsed as a version and be rejected.
 * To add v2: add "2" here and new mappings in controller.resource.v2.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_V1 = "1";
    private static final String API_PREFIX = "/api/";

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .usePathSegment(1, path -> path.pathWithinApplication().value().startsWith(API_PREFIX))
                .setVersionRequired(false)   // non-API paths carry no version; /api/** paths always have one
                .addSupportedVersions(API_V1);
    }
}
