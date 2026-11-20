package com.socialhub.notificationservice.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.socialhub.common.client.InternalApi;
import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import com.socialhub.common.web.ApiResponse;
import com.socialhub.notificationservice.store.NotificationDao;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Turns domain events into notifications. One transaction per event: the processed_events
 * marker and the inserted notifications commit together (exactly-once effect).
 *
 * Mapping:
 *  USER_FOLLOWED        -> followee        FOLLOW         (deduped per follower)
 *  CHANNEL_SUBSCRIBED   -> channel owner   NEW_SUBSCRIBER (deduped per subscriber)
 *  REACTION_SET LIKE    -> content owner   LIKE           (deduped per liker + target)
 *  COMMENT_CREATED      -> content owner   COMMENT; replies -> parent author (+ replied-to user) REPLY
 *  USER_MENTIONED       -> mentioned user  MENTION
 *  VIDEO_PUBLISHED      -> every subscriber NEW_VIDEO     (paged through video-service)
 *  CONTENT_REMOVED      -> content owner   CONTENT_REMOVED
 *  USER_REGISTERED      -> stores the email for delivery
 */
@Component
public class NotificationEventsConsumer {

    private static final String CONSUMER = "notification-service.events";
    private static final ParameterizedTypeReference<ApiResponse<List<Long>>> IDS = new ParameterizedTypeReference<>() {};

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChannelInfo(Long id, Long ownerId) {}

    private final EventJson json;
    private final Idempotency idempotency;
    private final Notifier notifier;
    private final NotificationDao dao;
    private final InternalApi api;

    public NotificationEventsConsumer(EventJson json, Idempotency idempotency, Notifier notifier,
                                      NotificationDao dao, InternalApi api) {
        this.json = json;
        this.idempotency = idempotency;
        this.notifier = notifier;
        this.dao = dao;
        this.api = api;
    }

    @KafkaListener(topics = {Topics.USER_EVENTS, Topics.VIDEO_EVENTS, Topics.POST_EVENTS, Topics.ENGAGEMENT_EVENTS,
            Topics.ACCOUNT_EVENTS, Topics.MODERATION_EVENTS})
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        if (!relevant(e.type())) return;
        if (!idempotency.firstTime(CONSUMER, e.eventId())) return;

        switch (e.type()) {
            case "USER_REGISTERED" -> dao.upsertContact(e.longField("userId"), e.stringField("email"), e.stringField("username"));
            case "USER_FOLLOWED" -> {
                long follower = e.longField("followerId");
                long followee = e.longField("followeeId");
                notifier.notify(followee, "FOLLOW", follower, "USER", follower, null, null, "FOLLOW:" + follower);
            }
            case "CHANNEL_SUBSCRIBED" -> {
                long sub = e.longField("subscriberId");
                notifier.notify(e.longField("channelOwnerId"), "NEW_SUBSCRIBER", sub, "CHANNEL", e.longField("channelId"),
                        null, null, "SUB:" + e.longField("channelId") + ":" + sub);
            }
            case "REACTION_SET" -> {
                if (!"LIKE".equals(e.stringField("kind"))) return;
                long user = e.longField("userId");
                String tt = e.stringField("targetType");
                long tid = e.longField("targetId");
                notifier.notify(e.longField("targetOwnerId"), "LIKE", user, tt, tid, null, null,
                        "LIKE:" + tt + ":" + tid + ":" + user);
            }
            case "COMMENT_CREATED" -> comment(e);
            case "USER_MENTIONED" -> notifier.notify(e.longField("mentionedUserId"), "MENTION", e.longField("authorId"),
                    "POST", e.longField("postId"), null, null, "MENTION:" + e.longField("postId"));
            case "VIDEO_PUBLISHED" -> newVideo(e);
            case "CONTENT_REMOVED" -> notifier.notify(e.longField("ownerId"), "CONTENT_REMOVED", null,
                    e.stringField("targetType"), e.longField("targetId"), null, e.stringField("reason"),
                    "REMOVED:" + e.stringField("targetType") + ":" + e.longField("targetId"));
            default -> { }
        }
    }

    private void comment(EventEnvelope e) {
        long author = e.longField("authorId");
        String tt = e.stringField("targetType");
        long tid = e.longField("targetId");
        long commentId = e.longField("commentId");
        String preview = e.stringField("bodyPreview");
        if (e.longField("parentId") == null) {
            notifier.notify(e.longField("targetOwnerId"), "COMMENT", author, tt, tid, commentId, preview, null);
            return;
        }
        Long parentAuthor = e.longField("parentAuthorId");
        Long repliedTo = e.longField("replyToUserId");
        if (parentAuthor != null) notifier.notify(parentAuthor, "REPLY", author, tt, tid, commentId, preview, null);
        if (repliedTo != null && !Objects.equals(repliedTo, parentAuthor)) {
            notifier.notify(repliedTo, "REPLY", author, tt, tid, commentId, preview, null);
        }
    }

    /** Fan out to all subscribers, 1,000 at a time (keyset pages from video-service). */
    private void newVideo(EventEnvelope e) {
        long channel = e.longField("channelId");
        long owner = e.longField("ownerId");
        long video = e.longField("videoId");
        String title = e.stringField("title");
        long after = 0;
        while (true) {
            List<Long> page = api.get("http://video-service/internal/channels/{id}/subscribers?after={a}&limit=1000",
                    IDS, channel, after).orElse(List.of());
            for (Long sub : page) {
                notifier.notify(sub, "NEW_VIDEO", owner, "VIDEO", video, null, title, "NEW_VIDEO:" + video);
            }
            if (page.size() < 1000) break;
            after = page.get(page.size() - 1);
        }
    }

    private static boolean relevant(String type) {
        return switch (type) {
            case "USER_REGISTERED", "USER_FOLLOWED", "CHANNEL_SUBSCRIBED", "REACTION_SET", "COMMENT_CREATED",
                 "USER_MENTIONED", "VIDEO_PUBLISHED", "CONTENT_REMOVED" -> true;
            default -> false;
        };
    }
}
