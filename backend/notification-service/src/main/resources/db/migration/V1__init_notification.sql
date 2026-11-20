-- notification-service owns notification_db.

CREATE TABLE notifications (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT UNSIGNED NOT NULL,          -- recipient
    type        VARCHAR(20)     NOT NULL,          -- FOLLOW, LIKE, COMMENT, REPLY, MENTION, NEW_VIDEO, NEW_SUBSCRIBER, CONTENT_REMOVED
    actor_id    BIGINT UNSIGNED NULL,              -- who did it (NULL for system notices)
    target_type VARCHAR(10)     NULL,              -- VIDEO | POST | COMMENT | CHANNEL | USER
    target_id   BIGINT UNSIGNED NULL,
    entity_id   BIGINT UNSIGNED NULL,              -- e.g. the comment id for COMMENT/REPLY
    preview     VARCHAR(200)    NULL,
    -- One notification per (recipient, type, actor, target) for idempotent types (FOLLOW, LIKE):
    -- unlike + like again must not notify twice. NULL for types that may repeat (comments).
    dedupe_key  VARCHAR(120)    NULL,
    read_at     DATETIME(3)     NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_notifications_dedupe (user_id, dedupe_key),
    INDEX idx_notifications_inbox (user_id, id),
    INDEX idx_notifications_unread (user_id, read_at)
);

-- Contact details mirrored from auth-service (USER_REGISTERED) for email delivery.
CREATE TABLE contacts (
    user_id       BIGINT UNSIGNED PRIMARY KEY,
    email         VARCHAR(255)    NOT NULL,
    username      VARCHAR(30)     NOT NULL,
    email_enabled BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE TABLE processed_events (
    consumer     VARCHAR(64) NOT NULL,
    event_id     CHAR(36)    NOT NULL,
    processed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (consumer, event_id)
);

-- Not used for publishing (notification-service produces no domain events),
-- but the shared outbox relay expects the table to exist.
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
