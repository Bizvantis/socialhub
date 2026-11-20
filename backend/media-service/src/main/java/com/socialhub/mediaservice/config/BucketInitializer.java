package com.socialhub.mediaservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Dev convenience: create the bucket in the local S3 if it does not exist.
 * Disabled in production, where the bucket is provisioned (private, encrypted,
 * CORS-restricted) as infrastructure, not by the application.
 */
@Component
@ConditionalOnProperty(prefix = "app.storage", name = "create-bucket", havingValue = "true")
public class BucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BucketInitializer.class);

    private final S3Client s3;
    private final StorageProperties props;

    public BucketInitializer(S3Client s3, StorageProperties props) {
        this.s3 = s3;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(props.bucket()).build());
        } catch (NoSuchBucketException e) {
            create();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) create();
            else log.warn("Could not check bucket {}: {}", props.bucket(), e.getMessage());
        }
    }

    private void create() {
        s3.createBucket(CreateBucketRequest.builder().bucket(props.bucket()).build());
        log.info("Created bucket {}", props.bucket());
    }
}
