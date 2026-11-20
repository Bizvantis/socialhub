package com.socialhub.engagementservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.engagementservice.domain.Types.TargetType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * When a video or post changes (made private, deleted, taken down), drop the cached target
 * so the next like/comment re-checks access immediately instead of after the 30 s TTL.
 * Pure cache invalidation, so it needs no idempotency marker.
 */
@Component
public class TargetEventsConsumer {

    private final EventJson json;
    private final TargetResolver targets;

    public TargetEventsConsumer(EventJson json, TargetResolver targets) {
        this.json = json;
        this.targets = targets;
    }

    @KafkaListener(topics = {Topics.VIDEO_EVENTS, Topics.POST_EVENTS})
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        switch (e.type()) {
            case "VIDEO_UPSERTED", "VIDEO_DELETED" -> targets.evict(TargetType.VIDEO, e.longField("videoId"));
            case "POST_UPSERTED", "POST_DELETED" -> targets.evict(TargetType.POST, e.longField("postId"));
            default -> { }
        }
    }
}
