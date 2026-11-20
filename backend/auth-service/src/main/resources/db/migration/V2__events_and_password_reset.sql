-- Steps 31-32: auth-service publishes account events and supports password reset.

CREATE TABLE password_reset_tokens (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,       -- SHA-256 of the emailed token; the raw token is never stored
    expires_at DATETIME(3)     NOT NULL,
    used_at    DATETIME(3)     NULL,
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_prt_hash (token_hash),
    INDEX idx_prt_user (user_id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
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
