-- DEV ONLY passwords. Production uses secrets from SSM.
CREATE DATABASE IF NOT EXISTS auth_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS user_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS media_db        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS video_db        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS post_db         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS engagement_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS notification_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS moderation_db   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'auth_user'@'%'         IDENTIFIED BY 'auth_dev_pw';
CREATE USER IF NOT EXISTS 'user_user'@'%'         IDENTIFIED BY 'user_dev_pw';
CREATE USER IF NOT EXISTS 'media_user'@'%'        IDENTIFIED BY 'media_dev_pw';
CREATE USER IF NOT EXISTS 'video_user'@'%'        IDENTIFIED BY 'video_dev_pw';
CREATE USER IF NOT EXISTS 'post_user'@'%'         IDENTIFIED BY 'post_dev_pw';
CREATE USER IF NOT EXISTS 'engagement_user'@'%'   IDENTIFIED BY 'engagement_dev_pw';
CREATE USER IF NOT EXISTS 'notification_user'@'%' IDENTIFIED BY 'notification_dev_pw';
CREATE USER IF NOT EXISTS 'moderation_user'@'%'   IDENTIFIED BY 'moderation_dev_pw';

GRANT ALL PRIVILEGES ON auth_db.*         TO 'auth_user'@'%';
GRANT ALL PRIVILEGES ON user_db.*         TO 'user_user'@'%';
GRANT ALL PRIVILEGES ON media_db.*        TO 'media_user'@'%';
GRANT ALL PRIVILEGES ON video_db.*        TO 'video_user'@'%';
GRANT ALL PRIVILEGES ON post_db.*         TO 'post_user'@'%';
GRANT ALL PRIVILEGES ON engagement_db.*   TO 'engagement_user'@'%';
GRANT ALL PRIVILEGES ON notification_db.* TO 'notification_user'@'%';
GRANT ALL PRIVILEGES ON moderation_db.*   TO 'moderation_user'@'%';
FLUSH PRIVILEGES;