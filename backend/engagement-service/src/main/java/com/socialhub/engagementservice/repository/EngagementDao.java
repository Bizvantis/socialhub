package com.socialhub.engagementservice.repository;

import com.socialhub.engagementservice.domain.Types.ReactionKind;
import com.socialhub.engagementservice.domain.Types.TargetType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

/**
 * Reactions, counters, bookmarks and shares in plain SQL. Every write is idempotent
 * (primary keys + INSERT IGNORE / ON DUPLICATE KEY) and every counter change is an atomic
 * "col = col + n", so concurrent likes never lose an update.
 */
@Repository
public class EngagementDao {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public EngagementDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---- reactions -----------------------------------------------------

    public Optional<ReactionKind> reactionOf(long userId, TargetType type, long targetId) {
        List<String> k = jdbc.queryForList(
                "SELECT kind FROM reactions WHERE user_id = ? AND target_type = ? AND target_id = ? FOR UPDATE",
                String.class, userId, type.name(), targetId);
        return k.isEmpty() ? Optional.empty() : Optional.of(ReactionKind.valueOf(k.get(0)));
    }

    public void insertReaction(long userId, TargetType type, long targetId, ReactionKind kind) {
        jdbc.update("INSERT INTO reactions (user_id, target_type, target_id, kind) VALUES (?, ?, ?, ?)",
                userId, type.name(), targetId, kind.name());
    }

    public void changeReaction(long userId, TargetType type, long targetId, ReactionKind kind) {
        jdbc.update("UPDATE reactions SET kind = ?, created_at = CURRENT_TIMESTAMP(3) WHERE user_id = ? AND target_type = ? AND target_id = ?",
                kind.name(), userId, type.name(), targetId);
    }

    public boolean deleteReaction(long userId, TargetType type, long targetId) {
        return jdbc.update("DELETE FROM reactions WHERE user_id = ? AND target_type = ? AND target_id = ?",
                userId, type.name(), targetId) == 1;
    }

    public Map<Long, ReactionKind> myReactions(long userId, TargetType type, Collection<Long> ids) {
        Map<Long, ReactionKind> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        named.query("SELECT target_id, kind FROM reactions WHERE user_id = :u AND target_type = :t AND target_id IN (:ids)",
                new MapSqlParameterSource("u", userId).addValue("t", type.name()).addValue("ids", ids),
                rs -> { out.put(rs.getLong(1), ReactionKind.valueOf(rs.getString(2))); });
        return out;
    }

    // ---- counters ------------------------------------------------------

    /** Atomic counter change; creates the stats row on first use. */
    public void bump(TargetType type, long targetId, String column, int delta) {
        if (!Set.of("likes", "dislikes", "comments", "shares").contains(column)) {
            throw new IllegalArgumentException(column);
        }
        jdbc.update("INSERT INTO target_stats (target_type, target_id, " + column + ") VALUES (?, ?, GREATEST(?, 0)) " +
                        "ON DUPLICATE KEY UPDATE " + column + " = GREATEST(CAST(" + column + " AS SIGNED) + ?, 0)",
                type.name(), targetId, delta, delta);
    }

    public record Stats(long targetId, int likes, int dislikes, int comments, int shares) {}

    public Map<Long, Stats> stats(TargetType type, Collection<Long> ids) {
        Map<Long, Stats> out = new HashMap<>();
        if (ids.isEmpty()) return out;
        named.query("SELECT target_id, likes, dislikes, comments, shares FROM target_stats WHERE target_type = :t AND target_id IN (:ids)",
                new MapSqlParameterSource("t", type.name()).addValue("ids", ids),
                rs -> { out.put(rs.getLong(1), new Stats(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))); });
        return out;
    }

    // ---- bookmarks -----------------------------------------------------

    public boolean addBookmark(long userId, TargetType type, long targetId) {
        return jdbc.update("INSERT IGNORE INTO bookmarks (user_id, target_type, target_id) VALUES (?, ?, ?)",
                userId, type.name(), targetId) == 1;
    }

    public boolean removeBookmark(long userId, TargetType type, long targetId) {
        return jdbc.update("DELETE FROM bookmarks WHERE user_id = ? AND target_type = ? AND target_id = ?",
                userId, type.name(), targetId) == 1;
    }

    public Set<Long> bookmarked(long userId, TargetType type, Collection<Long> ids) {
        if (ids.isEmpty()) return Set.of();
        return new HashSet<>(named.queryForList(
                "SELECT target_id FROM bookmarks WHERE user_id = :u AND target_type = :t AND target_id IN (:ids)",
                new MapSqlParameterSource("u", userId).addValue("t", type.name()).addValue("ids", ids), Long.class));
    }

    public record BookmarkRow(TargetType type, long targetId, Instant savedAt) {}

    /** Keyset by save time (epoch millis cursor). */
    public List<BookmarkRow> bookmarks(long userId, TargetType type, Instant before, int limit) {
        String sql = "SELECT target_type, target_id, created_at FROM bookmarks WHERE user_id = ? " +
                (type == null ? "" : "AND target_type = ? ") + "AND created_at < ? ORDER BY created_at DESC LIMIT ?";
        Object[] args = type == null
                ? new Object[]{userId, java.sql.Timestamp.from(before), limit}
                : new Object[]{userId, type.name(), java.sql.Timestamp.from(before), limit};
        return jdbc.query(sql, (rs, i) -> new BookmarkRow(TargetType.valueOf(rs.getString(1)), rs.getLong(2),
                rs.getTimestamp(3).toInstant()), args);
    }

    // ---- shares --------------------------------------------------------

    public void insertShare(long userId, TargetType type, long targetId, String channel) {
        jdbc.update("INSERT INTO shares (user_id, target_type, target_id, channel) VALUES (?, ?, ?, ?)",
                userId, type.name(), targetId, channel);
    }
}
