package com.socialhub.searchservice.index;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.searchservice.es.EsClient;
import com.socialhub.searchservice.es.Indices;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps Elasticsearch in sync with MySQL (the source of truth) from Kafka events (Step 26).
 *
 * - Snapshot events (VIDEO_UPSERTED, POST_UPSERTED, PROFILE_UPDATED, CHANNEL_*) are indexed with
 *   external version = event "version": duplicates and out-of-order deliveries are rejected by ES.
 * - Content that stops being public (private, deleted, taken down) is deleted from the index.
 * - Counters (views, followers, subscribers) are approximate script increments between snapshots;
 *   each snapshot resets them to the exact value.
 * - A new consumer group starts from the earliest offset, so the indexes can be rebuilt from scratch
 *   by resetting this group (or deleting the indexes) while Kafka still retains the history.
 */
@Component
public class IndexingConsumer {

    private final EventJson json;
    private final EsClient es;

    /** Indices is injected only to guarantee indexes exist before the first message is consumed. */
    public IndexingConsumer(EventJson json, EsClient es, Indices indices) {
        this.json = json;
        this.es = es;
    }

    @KafkaListener(topics = {Topics.VIDEO_EVENTS, Topics.POST_EVENTS, Topics.USER_EVENTS, Topics.VIDEO_VIEWS})
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        switch (e.type()) {
            case "VIDEO_UPSERTED" -> video(e);
            case "VIDEO_DELETED" -> es.delete(Indices.VIDEOS, id(e, "videoId"), version(e));
            case "VIDEO_VIEWED" -> es.increment(Indices.VIDEOS, id(e, "videoId"), "viewCount", 1);
            case "CHANNEL_CREATED", "CHANNEL_UPDATED" -> channel(e);
            case "CHANNEL_SUBSCRIBED" -> es.increment(Indices.CHANNELS, id(e, "channelId"), "subscriberCount", 1);
            case "CHANNEL_UNSUBSCRIBED" -> es.increment(Indices.CHANNELS, id(e, "channelId"), "subscriberCount", -1);
            case "POST_UPSERTED" -> post(e);
            case "POST_DELETED" -> es.delete(Indices.POSTS, id(e, "postId"), version(e));
            case "PROFILE_UPDATED" -> user(e);
            case "USER_FOLLOWED" -> es.increment(Indices.USERS, id(e, "followeeId"), "followerCount", 1);
            case "USER_UNFOLLOWED" -> es.increment(Indices.USERS, id(e, "followeeId"), "followerCount", -1);
            default -> { }
        }
    }

    private void video(EventEnvelope e) {
        String id = id(e, "videoId");
        if (!e.boolField("listed")) {           // private / unlisted / processing / removed
            es.delete(Indices.VIDEOS, id, version(e));
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("videoId", e.longField("videoId"));
        d.put("title", e.stringField("title"));
        d.put("description", e.stringField("description"));
        d.put("tags", e.payload().getOrDefault("tags", List.of()));
        d.put("category", e.stringField("category"));
        d.put("channelId", e.longField("channelId"));
        d.put("channelHandle", e.stringField("channelHandle"));
        d.put("channelName", e.stringField("channelName"));
        d.put("ownerId", e.longField("ownerId"));
        d.put("mediaId", e.longField("mediaId"));
        d.put("thumbnailMediaId", e.longField("thumbnailMediaId"));
        d.put("durationSeconds", e.longField("durationSeconds"));
        d.put("viewCount", e.longField("viewCount"));
        d.put("publishedAt", e.stringField("publishedAt"));
        es.index(Indices.VIDEOS, id, d, version(e));
    }

    private void post(EventEnvelope e) {
        String id = id(e, "postId");
        if (!e.boolField("listed")) {           // followers-only, processing, deleted, removed
            es.delete(Indices.POSTS, id, version(e));
            return;
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("postId", e.longField("postId"));
        d.put("authorId", e.longField("authorId"));
        d.put("type", e.stringField("type"));
        d.put("caption", e.stringField("caption"));
        d.put("hashtags", e.payload().getOrDefault("hashtags", List.of()));
        d.put("mediaIds", e.payload().getOrDefault("mediaIds", List.of()));
        d.put("createdAt", e.stringField("createdAt"));
        es.index(Indices.POSTS, id, d, version(e));
    }

    private void user(EventEnvelope e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("userId", e.longField("userId"));
        d.put("username", e.stringField("username"));
        d.put("displayName", e.stringField("displayName"));
        d.put("bio", e.stringField("bio"));
        d.put("avatarMediaId", e.longField("avatarMediaId"));
        d.put("followerCount", e.longField("followerCount"));
        d.put("privateAccount", e.boolField("privateAccount"));
        es.index(Indices.USERS, id(e, "userId"), d, version(e));
    }

    private void channel(EventEnvelope e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("channelId", e.longField("channelId"));
        d.put("ownerId", e.longField("ownerId"));
        d.put("handle", e.stringField("handle"));
        d.put("name", e.stringField("name"));
        d.put("description", e.stringField("description"));
        d.put("avatarMediaId", e.longField("avatarMediaId"));
        d.put("subscriberCount", e.longField("subscriberCount"));
        es.index(Indices.CHANNELS, id(e, "channelId"), d, version(e));
    }

    private static String id(EventEnvelope e, String field) {
        return String.valueOf(e.longField(field));
    }

    /** Snapshot version from the producer; falls back to the event time for older events. */
    private static long version(EventEnvelope e) {
        Long v = e.longField("version");
        return v != null ? v : e.occurredAt().toEpochMilli();
    }
}
