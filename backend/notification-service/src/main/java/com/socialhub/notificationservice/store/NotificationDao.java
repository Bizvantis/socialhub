package com.socialhub.notificationservice.store;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class NotificationDao {

    public record Notification(Long id, Long userId, String type, Long actorId, String targetType, Long targetId,
                               Long entityId, String preview, Instant readAt, Instant createdAt) {}

    public record Contact(long userId, String email, String username, boolean emailEnabled) {}

    private final JdbcTemplate jdbc;

    public NotificationDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * INSERT IGNORE on (user_id, dedupe_key): a second FOLLOW/LIKE from the same actor on the same
     * target is silently dropped. @return the new notification, or empty if it was a duplicate.
     */
    public Optional<Notification> insert(long userId, String type, Long actorId, String targetType, Long targetId,
                                         Long entityId, String preview, String dedupeKey) {
        Instant now = Instant.now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        int n = jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("""
                    INSERT IGNORE INTO notifications
                      (user_id, type, actor_id, target_type, target_id, entity_id, preview, dedupe_key, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setString(2, type);
            ps.setObject(3, actorId);
            ps.setString(4, targetType);
            ps.setObject(5, targetId);
            ps.setObject(6, entityId);
            ps.setString(7, preview == null ? null : preview.substring(0, Math.min(preview.length(), 200)));
            ps.setString(8, dedupeKey);
            ps.setTimestamp(9, Timestamp.from(now));
            return ps;
        }, keys);
        if (n == 0 || keys.getKey() == null) return Optional.empty();
        return Optional.of(new Notification(keys.getKey().longValue(), userId, type, actorId, targetType, targetId,
                entityId, preview, null, now));
    }

    public List<Notification> inbox(long userId, long beforeId, boolean unreadOnly, int limit) {
        return jdbc.query("""
                SELECT id, user_id, type, actor_id, target_type, target_id, entity_id, preview, read_at, created_at
                FROM notifications WHERE user_id = ? AND id < ?
                """ + (unreadOnly ? " AND read_at IS NULL" : "") + // text blocks strip trailing spaces: keep explicit separators
                " ORDER BY id DESC LIMIT ?", (rs, i) -> new Notification(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        (Long) rs.getObject(4, Long.class), rs.getString(5), (Long) rs.getObject(6, Long.class),
                        (Long) rs.getObject(7, Long.class), rs.getString(8),
                        rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(),
                        rs.getTimestamp(10).toInstant()),
                userId, beforeId, limit);
    }

    public long unreadCount(long userId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL",
                Long.class, userId);
        return n == null ? 0 : n;
    }

    public int markRead(long userId, long id) {
        return jdbc.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP(3) WHERE id = ? AND user_id = ? AND read_at IS NULL",
                id, userId);
    }

    public int markAllRead(long userId) {
        return jdbc.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP(3) WHERE user_id = ? AND read_at IS NULL", userId);
    }

    /** Keep inboxes bounded: delete read notifications older than 90 days. */
    public int purgeOld() {
        return jdbc.update("DELETE FROM notifications WHERE read_at IS NOT NULL AND created_at < NOW() - INTERVAL 90 DAY LIMIT 10000");
    }

    // ---- contacts ------------------------------------------------------

    public void upsertContact(long userId, String email, String username) {
        jdbc.update("""
                INSERT INTO contacts (user_id, email, username) VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE email = VALUES(email), username = VALUES(username)""", userId, email, username);
    }

    public Optional<Contact> contact(long userId) {
        List<Contact> c = jdbc.query("SELECT user_id, email, username, email_enabled FROM contacts WHERE user_id = ?",
                (rs, i) -> new Contact(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), userId);
        return c.isEmpty() ? Optional.empty() : Optional.of(c.get(0));
    }

    public void setEmailEnabled(long userId, boolean enabled) {
        jdbc.update("UPDATE contacts SET email_enabled = ? WHERE user_id = ?", enabled, userId);
    }
}
