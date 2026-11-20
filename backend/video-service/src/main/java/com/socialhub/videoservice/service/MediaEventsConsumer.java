package com.socialhub.videoservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors the processing result of uploaded files onto videos.
 * Consumer group "video-service"; at-least-once delivery made exactly-once in effect
 * by the processed_events marker written in the same transaction.
 */
@Component
public class MediaEventsConsumer {

    private static final String CONSUMER = "video-service.media-events";

    private final EventJson json;
    private final Idempotency idempotency;
    private final VideoService videos;

    public MediaEventsConsumer(EventJson json, Idempotency idempotency, VideoService videos) {
        this.json = json;
        this.idempotency = idempotency;
        this.videos = videos;
    }

    @KafkaListener(topics = Topics.MEDIA_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        if (!"VIDEO".equals(e.stringField("purpose"))) return;
        boolean ready = "MEDIA_READY".equals(e.type());
        boolean failed = "MEDIA_FAILED".equals(e.type());
        if (!ready && !failed) return;
        if (!idempotency.firstTime(CONSUMER, e.eventId())) return;

        Long duration = e.longField("durationSeconds");
        videos.onMediaProcessed(e.longField("mediaId"), ready, duration == null ? null : duration.intValue());
    }
}
