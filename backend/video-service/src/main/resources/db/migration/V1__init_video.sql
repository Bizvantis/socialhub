-- video-service owns video_db: channels, subscriptions, videos, playlists, viewer library.
-- Likes/dislikes and comment counts are owned by engagement-service (single owner per fact).

CREATE TABLE channels (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    owner_id         BIGINT UNSIGNED NOT NULL,
    handle           VARCHAR(30)     NOT NULL,          -- @handle, unique, URL-safe
    name             VARCHAR(80)     NOT NULL,
    description      VARCHAR(1000)   NULL,
    avatar_media_id  BIGINT UNSIGNED NULL,
    banner_media_id  BIGINT UNSIGNED NULL,
    subscriber_count INT UNSIGNED    NOT NULL DEFAULT 0, -- denormalised, updated atomically
    video_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_channels_owner (owner_id),             -- one channel per user
    UNIQUE KEY uk_channels_handle (handle)
);

CREATE TABLE subscriptions (
    subscriber_id BIGINT UNSIGNED NOT NULL,
    channel_id    BIGINT UNSIGNED NOT NULL,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (subscriber_id, channel_id),             -- no duplicate subscriptions
    INDEX idx_subs_channel (channel_id, created_at),
    CONSTRAINT fk_subs_channel FOREIGN KEY (channel_id) REFERENCES channels (id) ON DELETE CASCADE
);

CREATE TABLE videos (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    channel_id         BIGINT UNSIGNED NOT NULL,
    owner_id           BIGINT UNSIGNED NOT NULL,
    media_id           BIGINT UNSIGNED NOT NULL,          -- the uploaded file (media-service)
    thumbnail_media_id BIGINT UNSIGNED NULL,              -- custom thumbnail; else auto-generated
    title              VARCHAR(150)    NOT NULL,
    description        TEXT            NULL,
    category           VARCHAR(20)     NOT NULL,
    visibility         VARCHAR(10)     NOT NULL,          -- PUBLIC | UNLISTED | PRIVATE
    status             VARCHAR(12)     NOT NULL,          -- PROCESSING | READY | FAILED (mirrors media)
    duration_seconds   INT UNSIGNED    NULL,
    view_count         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    published_at       DATETIME(3)     NULL,              -- first time it became READY + PUBLIC
    removed            BOOLEAN         NOT NULL DEFAULT FALSE, -- taken down by moderation
    deleted            BOOLEAN         NOT NULL DEFAULT FALSE, -- soft delete by owner
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_videos_media (media_id),
    INDEX idx_videos_channel (channel_id, deleted, created_at),
    INDEX idx_videos_public (visibility, status, deleted, removed, published_at),
    INDEX idx_videos_category (category, visibility, status, published_at),
    INDEX idx_videos_popular (view_count),
    CONSTRAINT fk_videos_channel FOREIGN KEY (channel_id) REFERENCES channels (id)
);

CREATE TABLE video_tags (
    video_id BIGINT UNSIGNED NOT NULL,
    tag      VARCHAR(40)     NOT NULL,
    PRIMARY KEY (video_id, tag),
    INDEX idx_tags_tag (tag),
    CONSTRAINT fk_tags_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

CREATE TABLE playlists (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    owner_id    BIGINT UNSIGNED NOT NULL,
    title       VARCHAR(100)    NOT NULL,
    description VARCHAR(1000)   NULL,
    visibility  VARCHAR(10)     NOT NULL,
    video_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_playlists_owner (owner_id, updated_at)
);

CREATE TABLE playlist_videos (
    playlist_id BIGINT UNSIGNED NOT NULL,
    video_id    BIGINT UNSIGNED NOT NULL,
    position    INT UNSIGNED    NOT NULL,
    added_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (playlist_id, video_id),                  -- a video appears once per playlist
    INDEX idx_pv_order (playlist_id, position),
    CONSTRAINT fk_pv_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_pv_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

CREATE TABLE watch_history (
    user_id          BIGINT UNSIGNED NOT NULL,
    video_id         BIGINT UNSIGNED NOT NULL,
    progress_seconds INT UNSIGNED    NOT NULL DEFAULT 0,  -- resume position
    last_watched_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, video_id),
    INDEX idx_history_recent (user_id, last_watched_at),
    CONSTRAINT fk_history_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

CREATE TABLE watch_later (
    user_id  BIGINT UNSIGNED NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    added_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, video_id),
    INDEX idx_wl_recent (user_id, added_at),
    CONSTRAINT fk_wl_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

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

CREATE TABLE processed_events (
    consumer     VARCHAR(64) NOT NULL,
    event_id     CHAR(36)    NOT NULL,
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer, event_id)
);
