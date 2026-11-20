package com.socialhub.videoservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies moderation takedowns/restores to videos (VIDEO_UPSERTED then updates search, feeds, media). */
@Component
public class ModerationConsumer {

    private final EventJson json;
    private final Idempotency idempotency;
    private final VideoService videos;

    public ModerationConsumer(EventJson json, Idempotency idempotency, VideoService videos) {
        this.json = json;
        this.idempotency = idempotency;
        this.videos = videos;
    }

    @KafkaListener(topics = Topics.MODERATION_EVENTS, groupId = "video-service.moderation")
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        boolean removed = "CONTENT_REMOVED".equals(e.type());
        if ((!removed && !"CONTENT_RESTORED".equals(e.type())) || !"VIDEO".equals(e.stringField("targetType"))) return;
        if (!idempotency.firstTime("video-service.moderation", e.eventId())) return;
        videos.setRemoved(e.longField("targetId"), removed);
    }
}
