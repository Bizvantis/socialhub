package com.socialhub.feedservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.feedservice.store.FeedStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * One consumer group ("feed-service") over every topic that shapes feeds.
 *
 * Idempotency without a database transaction:
 *  - graph, timeline and feed writes are naturally idempotent (SADD, ZADD with the same score,
 *    ZREM), so a redelivered event simply re-applies the same state;
 *  - counters (likes/comments/shares) are not, so they are guarded by a SET NX marker per event
 *    id checked BEFORE incrementing (at-most-once; fine for a ranking signal).
 * Failures are retried by the shared Kafka error handler and end in the dead-letter topic.
 */
@Component
public class FeedEventsConsumer {

    private final EventJson json;
    private final FeedStore store;
    private final FeedWriter writer;

    public FeedEventsConsumer(EventJson json, FeedStore store, FeedWriter writer) {
        this.json = json;
        this.store = store;
        this.writer = writer;
    }

    @KafkaListener(topics = {Topics.USER_EVENTS, Topics.VIDEO_EVENTS, Topics.POST_EVENTS, Topics.ENGAGEMENT_EVENTS})
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        if (handles(e.type())) apply(e);
    }

    void apply(EventEnvelope e) {
        switch (e.type()) {
            case "USER_FOLLOWED" -> writer.followed(e.longField("followerId"), e.longField("followeeId"));
            case "USER_UNFOLLOWED" -> writer.unfollowed(e.longField("followerId"), e.longField("followeeId"));
            case "CHANNEL_SUBSCRIBED" -> writer.subscribed(e.longField("subscriberId"), e.longField("channelId"));
            case "CHANNEL_UNSUBSCRIBED" -> writer.unsubscribed(e.longField("subscriberId"), e.longField("channelId"));
            case "POST_CREATED" -> writer.postCreated(e);
            case "POST_UPSERTED" -> writer.postUpserted(e);
            case "POST_DELETED" -> writer.postRemoved(e.longField("postId"), e.longField("authorId"));
            case "VIDEO_PUBLISHED" -> writer.videoPublished(e);
            case "VIDEO_UPSERTED" -> writer.videoUpserted(e);
            case "VIDEO_DELETED" -> writer.videoRemoved(e.longField("videoId"), e.longField("channelId"), e.longField("ownerId"));
            case "REACTION_SET", "REACTION_REMOVED", "COMMENT_CREATED", "SHARED" -> {
                if (store.firstTime("eng:" + e.eventId())) writer.engagement(e); // counters: strictly once
            }
            default -> { }
        }
    }

    private static boolean handles(String type) {
        return switch (type) {
            case "USER_FOLLOWED", "USER_UNFOLLOWED", "CHANNEL_SUBSCRIBED", "CHANNEL_UNSUBSCRIBED",
                 "POST_CREATED", "POST_UPSERTED", "POST_DELETED",
                 "VIDEO_PUBLISHED", "VIDEO_UPSERTED", "VIDEO_DELETED",
                 "REACTION_SET", "REACTION_REMOVED", "COMMENT_CREATED", "SHARED" -> true;
            default -> false;
        };
    }
}
