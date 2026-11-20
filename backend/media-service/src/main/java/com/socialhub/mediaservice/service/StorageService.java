package com.socialhub.mediaservice.service;

import com.socialhub.mediaservice.config.StorageProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Thin wrapper over S3 so the rest of the service never touches SDK types directly. */
@Service
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties props;

    public StorageService(S3Client s3, S3Presigner presigner, StorageProperties props) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
    }

    /**
     * Pre-signed PUT. Content-Type and Content-Length are part of the signature, so the
     * client cannot upload a different type or a bigger file than it declared.
     */
    public URL presignUpload(String key, String contentType, long sizeBytes) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(props.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(props.uploadUrlTtl())
                .putObjectRequest(put)
                .build()).url();
    }

    public URL presignDownload(String key) {
        return presignDownload(key, props.downloadUrlTtl());
    }

    public URL presignDownload(String key, Duration ttl) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(props.bucket()).key(key).build())
                .build()).url();
    }

    /** HeadObject: what actually arrived in the bucket (empty if nothing was uploaded). */
    public Optional<HeadObjectResponse> head(String key) {
        try {
            return Optional.of(s3.headObject(HeadObjectRequest.builder().bucket(props.bucket()).key(key).build()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return Optional.empty();
            throw e;
        }
    }

    public byte[] read(String key) {
        ResponseBytes<GetObjectResponse> bytes =
                s3.getObjectAsBytes(GetObjectRequest.builder().bucket(props.bucket()).key(key).build());
        return bytes.asByteArray();
    }

    /** Deletes every object under a prefix (original + thumbnail + HLS segments). */
    public void deletePrefix(String prefix) {
        String token = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(props.bucket()).prefix(prefix).continuationToken(token).build());
            List<ObjectIdentifier> ids = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build()).toList();
            if (!ids.isEmpty()) {
                s3.deleteObjects(DeleteObjectsRequest.builder().bucket(props.bucket())
                        .delete(Delete.builder().objects(ids).quiet(true).build()).build());
            }
            token = page.isTruncated() ? page.nextContinuationToken() : null;
        } while (token != null);
    }
}
