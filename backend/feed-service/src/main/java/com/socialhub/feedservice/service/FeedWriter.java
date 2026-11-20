package com.socialhub.feedservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.feedservice.FeedProperties;
import com.socialhub.feedservice.store.FeedStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Turns domain events into feed state (hybrid fan-out).
 *
 * Fan-out on write: a new post is ZADDed into every follower's feed immediately, so reading a
 * feed is one cheap ZREVRANGE. The cost is paid at write time: N followers means N writes.
 *
 * Fan-out on read: for "celebrities" (followers > threshold) that write cost would be millions
 * of ZADDs per post. Their items stay only in their own timeline and are merged into
 * followers' feeds when the feed is read (see FeedReader).
 */
@Service
public class FeedWriter {

    private final FeedStore store;
    private final FeedProperties props;

    public FeedWriter(FeedStore store, FeedProperties props) {
        this.store = store;
        this.props = props;
    }

    // ---- posts -----------------------------------------------------------

    public void postCreated(EventEnvelope e) {
        long postId = e.longField("postId");
        long author = e.longField("authorId");
        String key = "P:" + postId;
        double score = epochMs(e.stringField("createdAt"), e);

        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("type", "POST");
        snap.put("id", postId);
        snap.put("authorId", author);
        snap.put("postType", e.stringField("type"));
        snap.put("caption", e.stringField("caption"));
        snap.put("mediaIds", e.payload().get("mediaIds"));
        snap.put("hashtags", e.payload().get("hashtags"));
        snap.put("visibility", e.stringField("visibility"));
        snap.put("createdAt", (long) score);
        store.putItem(key, snap);

        store.addToTimeline("tl:u:" + author, key, score);
        if ("PUBLIC".equals(e.stringField("visibility"))) store.addToTimeline("tl:global", key, score);

        Set<String> targets = new HashSet<>();
        targets.add(String.valueOf(author)); // you see your own posts in your feed
        if (store.followerCount(author) <= props.celebrityThreshold()) {
            targets.addAll(store.followers(author));
        }
        store.fanOut(targets, key, score);
    }

    /** Snapshot update: re-render on edit; withdraw if it is no longer visible. */
    public void postUpserted(EventEnvelope e) {
        long postId = e.longField("postId");
        String key = "P:" + postId;
        boolean visible = "READY".equals(e.stringField("status")) && !e.boolField("deleted") && !e.boolField("removed");
        if (!visible) {
            postRemoved(postId, e.longField("authorId"));
            return;
        }
        Map<String, Map<String, Object>> existing = store.items(List.of(key));
        if (existing.isEmpty()) return; // not announced yet (POST_CREATED will add it)
        Map<String, Object> snap = new LinkedHashMap<>(existing.get(key));
        snap.put("caption", e.stringField("caption"));
        snap.put("hashtags", e.payload().get("hashtags"));
        snap.put("visibility", e.stringField("visibility"));
        store.putItem(key, snap);
        if (!"PUBLIC".equals(e.stringField("visibility"))) store.removeFromTimeline("tl:global", key);
    }

    public void postRemoved(long postId, long author) {
        String key = "P:" + postId;
        store.removeFromTimeline("tl:u:" + author, key);
        store.removeFromTimeline("tl:global", key);
        Set<String> targets = new HashSet<>(store.followers(author));
        targets.add(String.valueOf(author));
        store.unFanOut(targets, List.of(key));
        store.deleteItem(key);
    }

    // ---- videos ----------------------------------------------------------

    public void videoPublished(EventEnvelope e) {
        long videoId = e.longField("videoId");
        long channel = e.longField("channelId");
        String key = "V:" + videoId;
        double score = epochMs(e.stringField("publishedAt"), e);

        store.putItem(key, videoSnapshot(e, score));
        store.addToTimeline("tl:c:" + channel, key, score);
        store.addToTimeline("tl:global", key, score);

        Set<String> targets = new HashSet<>();
        targets.add(String.valueOf(e.longField("ownerId")));
        if (store.subscriberCount(channel) <= props.celebrityThreshold()) {
            targets.addAll(store.subscribers(channel));
        }
        store.fanOut(targets, key, score);
    }

    public void videoUpserted(EventEnvelope e) {
        long videoId = e.longField("videoId");
        String key = "V:" + videoId;
        if (!e.boolField("listed")) {
            videoRemoved(videoId, e.longField("channelId"), e.longField("ownerId"));
            return;
        }
        Map<String, Map<String, Object>> existing = store.items(List.of(key));
        if (existing.isEmpty()) return;
        Object createdAt = existing.get(key).get("createdAt");
        store.putItem(key, videoSnapshot(e, createdAt instanceof Number n ? n.doubleValue() : epochMs(null, e)));
    }

    public void videoRemoved(long videoId, long channel, long owner) {
        String key = "V:" + videoId;
        store.removeFromTimeline("tl:c:" + channel, key);
        store.removeFromTimeline("tl:global", key);
        Set<String> targets = new HashSet<>(store.subscribers(channel));
        targets.add(String.valueOf(owner));
        store.unFanOut(targets, List.of(key));
        store.deleteItem(key);
    }

    private static Map<String, Object> videoSnapshot(EventEnvelope e, double score) {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("type", "VIDEO");
        snap.put("id", e.longField("videoId"));
        snap.put("authorId", e.longField("ownerId"));
        snap.put("channelId", e.longField("channelId"));
        snap.put("channelHandle", e.stringField("channelHandle"));
        snap.put("channelName", e.stringField("channelName"));
        snap.put("title", e.stringField("title"));
        snap.put("mediaId", e.longField("mediaId"));
        snap.put("thumbnailMediaId", e.longField("thumbnailMediaId"));
        snap.put("durationSeconds", e.longField("durationSeconds"));
        snap.put("createdAt", (long) score);
        return snap;
    }

    // ---- graph -----------------------------------------------------------

    /** New follow: copy the followee's latest items so the feed is not empty until they post again. */
    public void followed(long follower, long followee) {
        store.follow(follower, followee);
        if (store.followerCount(followee) <= props.celebrityThreshold()) {
            store.copyInto(String.valueOf(follower),
                    store.newest("tl:u:" + followee, Double.POSITIVE_INFINITY, props.backfillOnFollow()));
        }
    }

    public void unfollowed(long follower, long followee) {
        store.unfollow(follower, followee);
        store.unFanOut(List.of(String.valueOf(follower)), store.timelineItems("tl:u:" + followee, props.timelineSize()));
    }

    public void subscribed(long user, long channel) {
        store.subscribe(user, channel);
        if (store.subscriberCount(channel) <= props.celebrityThreshold()) {
            store.copyInto(String.valueOf(user),
                    store.newest("tl:c:" + channel, Double.POSITIVE_INFINITY, props.backfillOnFollow()));
        }
    }

    public void unsubscribed(long user, long channel) {
        store.unsubscribe(user, channel);
        store.unFanOut(List.of(String.valueOf(user)), store.timelineItems("tl:c:" + channel, props.timelineSize()));
    }

    // ---- engagement signals ---------------------------------------------

    public void engagement(EventEnvelope e) {
        String type = e.stringField("targetType");
        if (!"POST".equals(type) && !"VIDEO".equals(type)) return;
        String item = (type.equals("POST") ? "P:" : "V:") + e.longField("targetId");
        Long user = e.longField("userId");
        Long owner = e.longField("targetOwnerId");
        switch (e.type()) {
            case "REACTION_SET" -> {
                if ("LIKE".equals(e.stringField("kind"))) store.bumpStat(item, "likes", 1);
                if ("LIKE".equals(e.stringField("previous"))) store.bumpStat(item, "likes", -1);
                if (user != null && owner != null) store.bumpAffinity(user, owner, 1);
            }
            case "REACTION_REMOVED" -> {
                if ("LIKE".equals(e.stringField("kind"))) store.bumpStat(item, "likes", -1);
            }
            case "COMMENT_CREATED" -> {
                store.bumpStat(item, "comments", 1);
                Long author = e.longField("authorId");
                if (author != null && owner != null) store.bumpAffinity(author, owner, 2);
            }
            case "SHARED" -> {
                store.bumpStat(item, "shares", 1);
                if (user != null && owner != null) store.bumpAffinity(user, owner, 3);
            }
            default -> { }
        }
    }

    private static double epochMs(String iso, EventEnvelope e) {
        try {
            if (iso != null) return Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            // fall back to the event time
        }
        return e.occurredAt() == null ? System.currentTimeMillis() : e.occurredAt().toEpochMilli();
    }
}
