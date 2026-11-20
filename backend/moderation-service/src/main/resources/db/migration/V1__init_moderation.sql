-- moderation-service owns moderation_db: user reports and the admin audit trail.

CREATE TABLE reports (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reporter_id     BIGINT UNSIGNED NOT NULL,
    target_type     VARCHAR(10)     NOT NULL,        -- VIDEO | POST | COMMENT | USER
    target_id       BIGINT UNSIGNED NOT NULL,
    target_owner_id BIGINT UNSIGNED NOT NULL,
    reason          VARCHAR(20)     NOT NULL,        -- SPAM, HARASSMENT, HATE, NUDITY, VIOLENCE, MISINFORMATION, COPYRIGHT, OTHER
    details         VARCHAR(1000)   NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'OPEN',   -- OPEN | DISMISSED | ACTIONED
    resolved_by     BIGINT UNSIGNED NULL,
    resolved_at     DATETIME(3)     NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_reports_once (reporter_id, target_type, target_id),   -- one report per user per target
    INDEX idx_reports_queue (status, target_type, target_id),
    INDEX idx_reports_reporter (reporter_id, id)
);

-- Append-only audit log of every admin action (who, what, why, when).
CREATE TABLE moderation_actions (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    admin_id    BIGINT UNSIGNED NOT NULL,
    action      VARCHAR(20)     NOT NULL,            -- REMOVE_CONTENT, RESTORE_CONTENT, SUSPEND_USER, REINSTATE_USER, DISMISS_REPORTS
    target_type VARCHAR(10)     NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    reason      VARCHAR(500)    NULL,
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_actions_recent (id),
    INDEX idx_actions_target (target_type, target_id)
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
