package com.socialhub.postservice.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

/** SQL for hashtag, mention and story-view tables (idempotent writes, keyset reads). */
@Repository
public class PostDao {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public PostDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    // ---- hashtags ------------------------------------------------------

    /** Links a post to its hashtags, creating unknown tags. Counter moves only for new links. */
    public void linkHashtags(long postId, Set<String> tags) {
        for (String tag : tags) {
            jdbc.update("INSERT IGNORE INTO hashtags (tag) VALUES (?)", tag);
            Long tagId = jdbc.queryForObject("SELECT id FROM hashtags WHERE tag = ?", Long.class, tag);
            if (jdbc.update("INSERT IGNORE INTO post_hashtags (post_id, hashtag_id) VALUES (?, ?)", postId, tagId) == 1) {
                jdbc.update("UPDATE hashtags SET post_count = post_count + 1 WHERE id = ?", tagId);
            }
        }
    }

    public void unlinkHashtags(long postId) {
        jdbc.update("""
                UPDATE hashtags h JOIN post_hashtags ph ON ph.hashtag_id = h.id
                SET h.post_count = GREATEST(h.post_count - 1, 0) WHERE ph.post_id = ?""", postId);
        jdbc.update("DELETE FROM post_hashtags WHERE post_id = ?", postId);
    }

    public List<String> hashtagsOf(long postId) {
        return jdbc.queryForList("""
                SELECT h.tag FROM post_hashtags ph JOIN hashtags h ON h.id = ph.hashtag_id
                WHERE ph.post_id = ? ORDER BY h.tag""", String.class, postId);
    }

    public Map<Long, List<String>> hashtagsOf(Collection<Long> postIds) {
        Map<Long, List<String>> out = new HashMap<>();
        if (postIds.isEmpty()) return out;
        named.query("""
                SELECT ph.post_id, h.tag FROM post_hashtags ph JOIN hashtags h ON h.id = ph.hashtag_id
                WHERE ph.post_id IN (:ids)""", new MapSqlParameterSource("ids", postIds),
                rs -> { out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)); });
        return out;
    }

    public Optional<Long> hashtagId(String tag) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM hashtags WHERE tag = ?", Long.class, tag);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    public record TagCount(String tag, int postCount) {}

    public List<TagCount> topHashtags(int limit) {
        return jdbc.query("SELECT tag, post_count FROM hashtags WHERE post_count > 0 ORDER BY post_count DESC LIMIT ?",
                (rs, i) -> new TagCount(rs.getString(1), rs.getInt(2)), limit);
    }

    public List<TagCount> hashtagsByPrefix(String prefix, int limit) {
        return jdbc.query("""
                SELECT tag, post_count FROM hashtags WHERE tag LIKE ? AND post_count > 0
                ORDER BY post_count DESC LIMIT ?""",
                (rs, i) -> new TagCount(rs.getString(1), rs.getInt(2)), prefix.replace("%", "") + "%", limit);
    }

    /** Public, ready posts with this hashtag, newest first, keyset by post id. */
    public List<Long> postIdsByHashtag(long tagId, long before, int limit) {
        return jdbc.queryForList("""
                SELECT p.id FROM post_hashtags ph JOIN posts p ON p.id = ph.post_id
                WHERE ph.hashtag_id = ? AND p.id < ? AND p.visibility = 'PUBLIC' AND p.status = 'READY'
                  AND p.deleted = FALSE AND p.removed = FALSE
                ORDER BY p.id DESC LIMIT ?""", Long.class, tagId, before, limit);
    }

    public Optional<Long> postIdByMedia(long mediaId) {
        List<Long> ids = jdbc.queryForList("SELECT post_id FROM post_media WHERE media_id = ?", Long.class, mediaId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    // ---- mentions ------------------------------------------------------

    public void replaceMentions(long postId, Collection<Long> userIds) {
        jdbc.update("DELETE FROM post_mentions WHERE post_id = ?", postId);
        for (Long u : userIds) {
            jdbc.update("INSERT IGNORE INTO post_mentions (post_id, user_id) VALUES (?, ?)", postId, u);
        }
    }

    public List<Long> mentionsOf(long postId) {
        return jdbc.queryForList("SELECT user_id FROM post_mentions WHERE post_id = ?", Long.class, postId);
    }

    public Map<Long, List<Long>> mentionsOf(Collection<Long> postIds) {
        Map<Long, List<Long>> out = new HashMap<>();
        if (postIds.isEmpty()) return out;
        named.query("SELECT post_id, user_id FROM post_mentions WHERE post_id IN (:ids)",
                new MapSqlParameterSource("ids", postIds),
                rs -> { out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getLong(2)); });
        return out;
    }

    public List<Long> postIdsMentioning(long userId, long before, int limit) {
        return jdbc.queryForList("""
                SELECT m.post_id FROM post_mentions m JOIN posts p ON p.id = m.post_id
                WHERE m.user_id = ? AND m.post_id < ? AND p.deleted = FALSE AND p.removed = FALSE
                ORDER BY m.post_id DESC LIMIT ?""", Long.class, userId, before, limit);
    }

    // ---- story views ---------------------------------------------------

    /** @return true the first time this viewer sees the story. */
    public boolean markStoryViewed(long storyId, long viewerId) {
        return jdbc.update("INSERT IGNORE INTO story_views (story_id, viewer_id) VALUES (?, ?)", storyId, viewerId) == 1;
    }

    public Set<Long> seenStoryIds(long viewerId, Collection<Long> storyIds) {
        if (storyIds.isEmpty()) return Set.of();
        return new HashSet<>(named.queryForList(
                "SELECT story_id FROM story_views WHERE viewer_id = :v AND story_id IN (:ids)",
                new MapSqlParameterSource("v", viewerId).addValue("ids", storyIds), Long.class));
    }

    public record Viewer(long viewerId, Instant viewedAt) {}

    public List<Viewer> storyViewers(long storyId, int limit) {
        return jdbc.query("""
                SELECT viewer_id, viewed_at FROM story_views WHERE story_id = ?
                ORDER BY viewed_at DESC LIMIT ?""",
                (rs, i) -> new Viewer(rs.getLong(1), rs.getTimestamp(2).toInstant()), storyId, limit);
    }
}
