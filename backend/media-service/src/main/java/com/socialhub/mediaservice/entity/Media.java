package com.socialhub.mediaservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaStatus status;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "playback_key")
    private String playbackKey;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    private Integer width;
    private Integer height;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Owner/admin only, e.g. the files of a PRIVATE video. Kept in sync from video-service events. */
    @Column(nullable = false)
    private boolean restricted;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** All files for one media item share this prefix (original, thumbnail, HLS output). */
    public String keyPrefix() {
        return objectKey.substring(0, objectKey.lastIndexOf('/') + 1);
    }
}
