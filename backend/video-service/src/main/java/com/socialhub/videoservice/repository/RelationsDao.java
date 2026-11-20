package com.socialhub.videoservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Plain-SQL access to the pure relationship tables (subscriptions, playlist items,
 * watch history, watch later). INSERT IGNORE / ON DUPLICATE KEY make every write
 * idempotent and race-free without loading entities first.
 */
@Repository
public class RelationsDao {

    private final JdbcTemplate jdbc;

    public RelationsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- subscriptions -------------------------------------------------

    /** @return true if a new subscription row was created (false if it already existed). */
    public boolean subscribe(long userId, long channelId) {
        return jdbc.update("INSERT IGNORE INTO subscriptions (subscriber_id, channel_id) VALUES (?, ?)",
                userId, channelId) == 1;
    }

    public boolean unsubscribe(long userId, long channelId) {
        return jdbc.update("DELETE FROM subscriptions WHERE subscriber_id = ? AND channel_id = ?",
                userId, channelId) == 1;
    }

    public boolean isSubscribed(long userId, long channelId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE subscriber_id = ? AND channel_id = ?",
                Integer.class, userId, channelId);
        return n != null && n > 0;
    }

    public List<Long> subscribedChannelIds(long userId, int limit, int offset) {
        return jdbc.queryForList("""
                SELECT channel_id FROM subscriptions WHERE subscriber_id = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?""", Long.class, userId, limit, offset);
    }

    /** Used by feed/notification fan-out through the internal API. */
    public List<Long> subscriberIds(long channelId, long afterUserId, int limit) {
        return jdbc.queryForList("""
                SELECT subscriber_id FROM subscriptions WHERE channel_id = ? AND subscriber_id > ?
                ORDER BY subscriber_id LIMIT ?""", Long.class, channelId, afterUserId, limit);
    }

    // ---- playlist items ------------------------------------------------

    public boolean addToPlaylist(long playlistId, long videoId) {
        return jdbc.update("""
                INSERT IGNORE INTO playlist_videos (playlist_id, video_id, position)
                SELECT ?, ?, COALESCE(MAX(position), 0) + 1 FROM playlist_videos WHERE playlist_id = ?""",
                playlistId, videoId, playlistId) == 1;
    }

    public boolean removeFromPlaylist(long playlistId, long videoId) {
        return jdbc.update("DELETE FROM playlist_videos WHERE playlist_id = ? AND video_id = ?",
                playlistId, videoId) == 1;
    }

    public List<Long> playlistVideoIds(long playlistId, int limit, int offset) {
        return jdbc.queryForList("""
                SELECT video_id FROM playlist_videos WHERE playlist_id = ?
                ORDER BY position LIMIT ? OFFSET ?""", Long.class, playlistId, limit, offset);
    }

    // ---- watch history -------------------------------------------------

    public void recordWatch(long userId, long videoId, int progressSeconds) {
        jdbc.update("""
                INSERT INTO watch_history (user_id, video_id, progress_seconds, last_watched_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE progress_seconds = VALUES(progress_seconds),
                                        last_watched_at = CURRENT_TIMESTAMP(3)""",
                userId, videoId, Math.max(progressSeconds, 0));
    }

    public record HistoryRow(long videoId, int progressSeconds, Instant watchedAt) {}

    public List<HistoryRow> history(long userId, int limit, int offset) {
        return jdbc.query("""
                SELECT video_id, progress_seconds, last_watched_at FROM watch_history
                WHERE user_id = ? ORDER BY last_watched_at DESC LIMIT ? OFFSET ?""",
                (rs, i) -> new HistoryRow(rs.getLong(1), rs.getInt(2), rs.getTimestamp(3).toInstant()),
                userId, limit, offset);
    }

    public Integer progress(long userId, long videoId) {
        List<Integer> p = jdbc.queryForList(
                "SELECT progress_seconds FROM watch_history WHERE user_id = ? AND video_id = ?",
                Integer.class, userId, videoId);
        return p.isEmpty() ? null : p.get(0);
    }

    public void removeFromHistory(long userId, long videoId) {
        jdbc.update("DELETE FROM watch_history WHERE user_id = ? AND video_id = ?", userId, videoId);
    }

    public int clearHistory(long userId) {
        return jdbc.update("DELETE FROM watch_history WHERE user_id = ?", userId);
    }

    // ---- watch later ---------------------------------------------------

    public boolean addWatchLater(long userId, long videoId) {
        return jdbc.update("INSERT IGNORE INTO watch_later (user_id, video_id) VALUES (?, ?)", userId, videoId) == 1;
    }

    public boolean removeWatchLater(long userId, long videoId) {
        return jdbc.update("DELETE FROM watch_later WHERE user_id = ? AND video_id = ?", userId, videoId) == 1;
    }

    public boolean inWatchLater(long userId, long videoId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM watch_later WHERE user_id = ? AND video_id = ?",
                Integer.class, userId, videoId);
        return n != null && n > 0;
    }

    public record LaterRow(long videoId, Instant addedAt) {}

    public List<LaterRow> watchLater(long userId, int limit, int offset) {
        return jdbc.query("""
                SELECT video_id, added_at FROM watch_later WHERE user_id = ?
                ORDER BY added_at DESC LIMIT ? OFFSET ?""",
                (rs, i) -> new LaterRow(rs.getLong(1), rs.getTimestamp(2).toInstant()), userId, limit, offset);
    }

    public static Timestamp ts(Instant i) {
        return Timestamp.from(i);
    }
}
