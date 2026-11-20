package com.socialhub.videoworker.storage;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Bean
    AwsCredentialsProvider awsCredentials(StorageProperties p) {
        if (p.accessKey() != null && !p.accessKey().isBlank()) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKey(), p.secretKey()));
        }
        // EC2 instance role / env vars / ~/.aws in production: no keys in config.
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    S3Client s3Client(StorageProperties p, AwsCredentialsProvider creds) {
        var b = S3Client.builder()
                .region(Region.of(p.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.pathStyle()).build())
                // Only add checksums when S3 requires them (keeps MinIO and pre-signed flows simple).
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED);
        if (notBlank(p.endpoint())) b.endpointOverride(URI.create(p.endpoint()));
        return b.build();
    }

    /** Signs URLs with the endpoint the browser uses, which may differ from the internal one. */
    @Bean
    S3Presigner s3Presigner(StorageProperties p, AwsCredentialsProvider creds) {
        var b = S3Presigner.builder()
                .region(Region.of(p.region()))
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.pathStyle()).build());
        String endpoint = notBlank(p.publicEndpoint()) ? p.publicEndpoint() : p.endpoint();
        if (notBlank(endpoint)) b.endpointOverride(URI.create(endpoint));
        return b.build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
