package com.socialhub.videoworker.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * S3 settings. Local dev points at MinIO; production points at AWS S3 by leaving
 * {@code endpoint} empty and using the EC2 instance role instead of static keys.
 *
 * @param endpoint        S3 API endpoint the service itself calls (e.g. http://minio:9000); empty = AWS
 * @param publicEndpoint  endpoint baked into pre-signed URLs, i.e. what the BROWSER can reach
 *                        (http://localhost:9000 in dev); empty = same as endpoint
 * @param accessKey       static credentials for MinIO; empty = AWS default credential chain
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String bucket,
        String region,
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        boolean pathStyle,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl) {

    public StorageProperties {
        if (region == null || region.isBlank()) region = "ap-south-1";
        if (uploadUrlTtl == null) uploadUrlTtl = Duration.ofMinutes(15);
        if (downloadUrlTtl == null) downloadUrlTtl = Duration.ofHours(1);
    }
}
