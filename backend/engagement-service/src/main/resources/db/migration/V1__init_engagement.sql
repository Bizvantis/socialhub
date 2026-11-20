-- engagement-service owns engagement_db: everything users do TO content
-- (comments, reactions, bookmarks, shares) and the counters derived from it.
-- Targets (videos, posts) live in other services and are referenced by (type, id).

CREATE TABLE comments (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    target_type      VARCHAR(10)     NOT NULL,      -- VIDEO | POST
    target_id        BIGINT UNSIGNED NOT NULL,
    target_owner_id  BIGINT UNSIGNED NOT NULL,      -- lets the creator moderate their comment section
    author_id        BIGINT UNSIGNED NOT NULL,
    parent_id        BIGINT UNSIGNED NULL,          -- NULL = top-level; replies are one level deep
    reply_to_user_id BIGINT UNSIGNED NULL,          -- "@name" when replying inside a thread
    body             VARCHAR(2200)   NOT NULL,
    like_count       INT UNSIGNED    NOT NULL DEFAULT 0,
    reply_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    removed          BOOLEAN         NOT NULL DEFAULT FALSE,
    edited_at        DATETIME(3)     NULL,
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_comments_thread (target_type, target_id, parent_id, id),   -- newest first, keyset
    INDEX idx_comments_top (target_type, target_id, parent_id, like_count),
    INDEX idx_comments_replies (parent_id, id),
    INDEX idx_comments_author (author_id, id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments (id)
);

-- One reaction per user per target (the primary key makes duplicates impossible).
CREATE TABLE reactions (
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(10)     NOT NULL,           -- VIDEO | POST | COMMENT
    target_id   BIGINT UNSIGNED NOT NULL,
    kind        VARCHAR(10)     NOT NULL,           -- LIKE | DISLIKE (dislike: videos only)
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, target_type, target_id),
    INDEX idx_reactions_target (target_type, target_id, kind)
);

-- Denormalised counters per target, updated atomically in the same transaction.
CREATE TABLE target_stats (
    target_type VARCHAR(10)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    likes       INT UNSIGNED    NOT NULL DEFAULT 0,
    dislikes    INT UNSIGNED    NOT NULL DEFAULT 0,
    comments    INT UNSIGNED    NOT NULL DEFAULT 0,
    shares      INT UNSIGNED    NOT NULL DEFAULT 0,
    PRIMARY KEY (target_type, target_id)
);

CREATE TABLE bookmarks (
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(10)     NOT NULL,           -- POST | VIDEO
    target_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, target_type, target_id),
    INDEX idx_bookmarks_user (user_id, target_type, created_at)
);

CREATE TABLE shares (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(10)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    channel     VARCHAR(20)     NOT NULL,           -- LINK | DIRECT | EXTERNAL
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_shares_target (target_type, target_id),
    INDEX idx_shares_user (user_id, created_at)
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
