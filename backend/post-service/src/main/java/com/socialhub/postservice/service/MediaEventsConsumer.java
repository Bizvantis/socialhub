package com.socialhub.postservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reels and story videos become visible once media-service reports transcoding finished. */
@Component
public class MediaEventsConsumer {

    private static final String CONSUMER = "post-service.media-events";

    private final EventJson json;
    private final Idempotency idempotency;
    private final PostService posts;
    private final StoryService stories;

    public MediaEventsConsumer(EventJson json, Idempotency idempotency, PostService posts, StoryService stories) {
        this.json = json;
        this.idempotency = idempotency;
        this.posts = posts;
        this.stories = stories;
    }

    @KafkaListener(topics = Topics.MEDIA_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        boolean ready = "MEDIA_READY".equals(e.type());
        if (!ready && !"MEDIA_FAILED".equals(e.type())) return;
        String purpose = e.stringField("purpose");
        if (!"REEL".equals(purpose) && !"STORY_VIDEO".equals(purpose)) return;
        if (!idempotency.firstTime(CONSUMER, e.eventId())) return;

        Long mediaId = e.longField("mediaId");
        if ("REEL".equals(purpose)) posts.onReelProcessed(mediaId, ready);
        else stories.onMediaProcessed(mediaId, ready);
    }
}
