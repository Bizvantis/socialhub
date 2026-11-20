-- media-service owns media_db. Files live in S3; this table holds only metadata.
CREATE TABLE media (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    owner_id          BIGINT UNSIGNED NOT NULL,
    purpose           VARCHAR(20)     NOT NULL,   -- AVATAR, BANNER, POST_IMAGE, REEL, STORY_IMAGE, STORY_VIDEO, VIDEO, THUMBNAIL
    media_type        VARCHAR(10)     NOT NULL,   -- IMAGE | VIDEO
    content_type      VARCHAR(100)    NOT NULL,
    size_bytes        BIGINT UNSIGNED NOT NULL,   -- declared at upload start, verified with HeadObject
    object_key        VARCHAR(255)    NOT NULL,
    original_filename VARCHAR(255)    NULL,
    status            VARCHAR(20)     NOT NULL,   -- PENDING, UPLOADED, PROCESSING, READY, FAILED, DELETED
    thumbnail_key     VARCHAR(255)    NULL,
    playback_key      VARCHAR(255)    NULL,       -- HLS master playlist for videos
    duration_seconds  INT UNSIGNED    NULL,
    width             INT UNSIGNED    NULL,
    height            INT UNSIGNED    NULL,
    failure_reason    VARCHAR(255)    NULL,
    restricted        BOOLEAN         NOT NULL DEFAULT FALSE, -- owner/admin only (e.g. private video); set via events
    created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    UNIQUE KEY uk_media_object_key (object_key),
    INDEX idx_media_owner (owner_id, created_at),
    INDEX idx_media_status (status, created_at)
);

-- Transactional outbox (see common/outbox). One per producing service.
CREATE TABLE outbox_events (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id     CHAR(36)     NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(64)  NOT NULL,
    payload      MEDIUMTEXT   NOT NULL,
    created_at   DATETIME(3)  NOT NULL,
    published_at DATETIME(3)  NULL,
    UNIQUE KEY uk_outbox_event (event_id),
    INDEX idx_outbox_unpublished (published_at, id)
);

-- Consumer idempotency markers (see common/idempotency).
CREATE TABLE processed_events (
    consumer     VARCHAR(64) NOT NULL,
    event_id     CHAR(36)    NOT NULL,
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer, event_id)
);
