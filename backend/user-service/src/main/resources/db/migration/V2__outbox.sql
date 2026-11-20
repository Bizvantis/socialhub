-- Step 21: user-service starts publishing domain events (follows, profile updates)
-- through the transactional outbox shared by every service (see backend/common).
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
