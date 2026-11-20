package com.socialhub.videoservice.service;

import com.socialhub.common.event.Topics;
import com.socialhub.common.outbox.OutboxWriter;
import com.socialhub.videoservice.entity.Channel;
import com.socialhub.videoservice.entity.Video;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds and writes video-domain events through the outbox (same transaction as the change).
 *
 * VIDEO_UPSERTED carries a full snapshot plus a monotonically increasing "version" so that
 * consumers maintaining read models (Elasticsearch, media restrictions) can drop stale
 * or out-of-order updates instead of applying them blindly.
 */
@Component
public class VideoEvents {

    private final OutboxWriter outbox;

    public VideoEvents(OutboxWriter outbox) {
        this.outbox = outbox;
    }

    public void channelCreated(Channel c) {
        outbox.publish(Topics.VIDEO_EVENTS, "CHANNEL_CREATED", c.getId(), channelPayload(c));
    }

    public void channelUpdated(Channel c) {
        outbox.publish(Topics.VIDEO_EVENTS, "CHANNEL_UPDATED", c.getId(), channelPayload(c));
    }

    public void subscribed(Channel c, long subscriberId, boolean subscribed) {
        Map<String, Object> p = new HashMap<>();
        p.put("channelId", c.getId());
        p.put("channelOwnerId", c.getOwnerId());
        p.put("subscriberId", subscriberId);
        outbox.publish(Topics.VIDEO_EVENTS, subscribed ? "CHANNEL_SUBSCRIBED" : "CHANNEL_UNSUBSCRIBED", c.getId(), p);
    }

    /** First time a video becomes publicly listed: drives feeds and subscriber notifications. */
    public void published(Video v) {
        outbox.publish(Topics.VIDEO_EVENTS, "VIDEO_PUBLISHED", v.getId(), snapshot(v));
    }

    public void upserted(Video v) {
        outbox.publish(Topics.VIDEO_EVENTS, "VIDEO_UPSERTED", v.getId(), snapshot(v));
    }

    public void deleted(Video v) {
        Map<String, Object> p = snapshot(v);
        p.put("deleted", true);
        outbox.publish(Topics.VIDEO_EVENTS, "VIDEO_DELETED", v.getId(), p);
    }

    private static Map<String, Object> channelPayload(Channel c) {
        Map<String, Object> p = new HashMap<>();
        p.put("channelId", c.getId());
        p.put("ownerId", c.getOwnerId());
        p.put("handle", c.getHandle());
        p.put("name", c.getName());
        p.put("description", c.getDescription());
        p.put("avatarMediaId", c.getAvatarMediaId());
        p.put("subscriberCount", c.getSubscriberCount());
        p.put("version", System.currentTimeMillis());
        return p;
    }

    static Map<String, Object> snapshot(Video v) {
        Map<String, Object> p = new HashMap<>();
        p.put("videoId", v.getId());
        p.put("channelId", v.getChannel().getId());
        p.put("channelHandle", v.getChannel().getHandle());
        p.put("channelName", v.getChannel().getName());
        p.put("ownerId", v.getOwnerId());
        p.put("title", v.getTitle());
        p.put("description", v.getDescription());
        p.put("tags", new ArrayList<>(v.getTags()));
        p.put("category", v.getCategory().name());
        p.put("visibility", v.getVisibility().name());
        p.put("status", v.getStatus().name());
        p.put("listed", v.isListed());
        p.put("deleted", v.isDeleted());
        p.put("removed", v.isRemoved());
        p.put("mediaId", v.getMediaId());
        p.put("thumbnailMediaId", v.getThumbnailMediaId());
        p.put("durationSeconds", v.getDurationSeconds());
        p.put("viewCount", v.getViewCount());
        p.put("publishedAt", v.getPublishedAt() == null ? null : v.getPublishedAt().toString());
        p.put("version", System.currentTimeMillis());
        return p;
    }
}
