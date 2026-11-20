package com.socialhub.mediaservice.dto;

import com.socialhub.mediaservice.entity.MediaPurpose;
import com.socialhub.mediaservice.entity.MediaStatus;
import com.socialhub.mediaservice.entity.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class MediaDtos {

    private MediaDtos() {}

    public record CreateUploadRequest(
            @NotNull MediaPurpose purpose,
            @NotBlank @Size(max = 100) String contentType,
            @Positive long sizeBytes,
            @Size(max = 255) String fileName) {}

    /** Everything the client needs to PUT the file straight to S3. */
    public record UploadTicket(
            Long mediaId,
            String uploadUrl,
            String method,
            Map<String, String> headers,
            Instant expiresAt) {}

    public record MediaResponse(
            Long id,
            Long ownerId,
            MediaPurpose purpose,
            MediaType mediaType,
            String contentType,
            long sizeBytes,
            MediaStatus status,
            String url,
            String thumbnailUrl,
            String playbackUrl,
            Integer durationSeconds,
            Integer width,
            Integer height,
            Instant createdAt) {}

    public record BatchRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> ids) {}

    /** Service-to-service view used to validate attachments (no URLs). */
    public record MediaInfo(
            Long id,
            Long ownerId,
            MediaPurpose purpose,
            MediaType mediaType,
            MediaStatus status,
            Integer durationSeconds) {}
}
