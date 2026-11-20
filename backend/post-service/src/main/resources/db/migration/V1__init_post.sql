-- post-service owns post_db: Instagram-style posts (image, carousel, reel), hashtags,
-- mentions and 24-hour stories. Likes/comments/bookmarks live in engagement-service.

CREATE TABLE posts (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    author_id   BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(10)     NOT NULL,           -- IMAGE | CAROUSEL | REEL
    caption     VARCHAR(2200)   NULL,               -- Instagram's caption limit
    visibility  VARCHAR(10)     NOT NULL,           -- PUBLIC | FOLLOWERS
    status      VARCHAR(12)     NOT NULL,           -- PROCESSING (reel transcoding) | READY | FAILED
    removed     BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted     BOOLEAN         NOT NULL DEFAULT FALSE,
    edited_at   DATETIME(3)     NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_posts_author (author_id, deleted, id),         -- profile grid, keyset by id
    INDEX idx_posts_explore (visibility, status, deleted, removed, id),
    INDEX idx_posts_reels (type, visibility, status, deleted, removed, id)
);

-- Ordered media of a post (1 for IMAGE/REEL, 2-10 for CAROUSEL).
CREATE TABLE post_media (
    post_id  BIGINT UNSIGNED NOT NULL,
    position INT UNSIGNED    NOT NULL,
    media_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (post_id, position),
    UNIQUE KEY uk_post_media (media_id),                     -- a file belongs to one post
    CONSTRAINT fk_pm_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);

CREATE TABLE hashtags (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    tag        VARCHAR(50)     NOT NULL,                     -- stored lower-case, without '#'
    post_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_hashtags_tag (tag),
    INDEX idx_hashtags_popular (post_count)
);

CREATE TABLE post_hashtags (
    post_id    BIGINT UNSIGNED NOT NULL,
    hashtag_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (post_id, hashtag_id),
    INDEX idx_ph_tag (hashtag_id, post_id),                  -- hashtag page, keyset by post id
    CONSTRAINT fk_ph_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    CONSTRAINT fk_ph_tag FOREIGN KEY (hashtag_id) REFERENCES hashtags (id)
);

CREATE TABLE post_mentions (
    post_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (post_id, user_id),
    INDEX idx_mentions_user (user_id, post_id),
    CONSTRAINT fk_mentions_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE
);

CREATE TABLE stories (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    author_id  BIGINT UNSIGNED NOT NULL,
    media_id   BIGINT UNSIGNED NOT NULL,
    media_type VARCHAR(10)     NOT NULL,                     -- IMAGE | VIDEO
    caption    VARCHAR(500)    NULL,
    ready      BOOLEAN         NOT NULL,                     -- videos wait for transcoding
    deleted    BOOLEAN         NOT NULL DEFAULT FALSE,
    view_count INT UNSIGNED    NOT NULL DEFAULT 0,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3)     NOT NULL,                     -- created_at + 24h
    UNIQUE KEY uk_stories_media (media_id),
    INDEX idx_stories_author (author_id, expires_at),
    INDEX idx_stories_expiry (expires_at)
);

CREATE TABLE story_views (
    story_id  BIGINT UNSIGNED NOT NULL,
    viewer_id BIGINT UNSIGNED NOT NULL,
    viewed_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (story_id, viewer_id),                       -- each viewer counted once
    INDEX idx_sv_recent (story_id, viewed_at),
    CONSTRAINT fk_sv_story FOREIGN KEY (story_id) REFERENCES stories (id) ON DELETE CASCADE
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
