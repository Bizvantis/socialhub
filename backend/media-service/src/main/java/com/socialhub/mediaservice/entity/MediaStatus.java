package com.socialhub.mediaservice.entity;

/**
 * Upload lifecycle:
 * <pre>
 * PENDING --complete--> READY                       (images)
 * PENDING --complete--> UPLOADED -> PROCESSING -> READY | FAILED   (videos, via video-worker)
 * PENDING --24h, never completed--> FAILED          (cleanup job)
 * any --owner deletes--> DELETED
 * </pre>
 */
public enum MediaStatus {
    PENDING,
    UPLOADED,
    PROCESSING,
    READY,
    FAILED,
    DELETED;

    /** Media that other services may attach to posts, videos or profiles. */
    public boolean attachable() {
        return this == UPLOADED || this == PROCESSING || this == READY;
    }
}
