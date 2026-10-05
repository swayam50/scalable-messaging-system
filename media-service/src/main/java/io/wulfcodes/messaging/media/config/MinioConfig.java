package io.wulfcodes.messaging.media.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Two MinIO clients with the same credentials:
 * <ul>
 *   <li>{@code storageClient}: talks to MinIO over the internal network (bucket setup, stat checks);</li>
 *   <li>{@code presignClient}: only SIGNS URLs for the public endpoint browsers use. Signing is a local
 *       computation, and the region is fixed so the SDK never makes a network call to look it up.</li>
 * </ul>
 */
@Configuration
public class MinioConfig {

    public static final String STORAGE_CLIENT = "storageClient";
    public static final String PRESIGN_CLIENT = "presignClient";

    @Bean
    @Qualifier(STORAGE_CLIENT)
    public MinioClient storageClient(MediaProperties properties) {
        MediaProperties.Storage storage = properties.storage();
        return MinioClient.builder()
                .endpoint(storage.internalEndpoint())
                .region(storage.region())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
    }

    @Bean
    @Qualifier(PRESIGN_CLIENT)
    public MinioClient presignClient(MediaProperties properties) {
        MediaProperties.Storage storage = properties.storage();
        return MinioClient.builder()
                .endpoint(storage.publicEndpoint())
                .region(storage.region())
                .credentials(storage.accessKey(), storage.secretKey())
                .build();
    }
}
