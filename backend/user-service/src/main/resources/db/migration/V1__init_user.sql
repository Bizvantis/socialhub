CREATE TABLE profiles (
    user_id BIGINT UNSIGNED PRIMARY KEY,
    username VARCHAR(30) NOT NULL UNIQUE,
    display_name VARCHAR(50) NOT NULL,
    bio VARCHAR(300) NULL,
    avatar_media_id BIGINT UNSIGNED NULL,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    follower_count INT UNSIGNED NOT NULL DEFAULT 0,
    following_count INT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3)
);

CREATE TABLE follows (
    follower_id BIGINT UNSIGNED NOT NULL,
    followee_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ACCEPTED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (follower_id, followee_id),

    INDEX idx_follows_followee (followee_id, created_at),

    CONSTRAINT chk_no_self_follow
        CHECK (follower_id <> followee_id)
);