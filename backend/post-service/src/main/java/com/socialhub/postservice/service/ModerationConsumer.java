package com.socialhub.postservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies moderation takedowns/restores to posts (POST_UPSERTED then updates search and feeds). */
@Component
public class ModerationConsumer {

    private final EventJson json;
    private final Idempotency idempotency;
    private final PostService posts;

    public ModerationConsumer(EventJson json, Idempotency idempotency, PostService posts) {
        this.json = json;
        this.idempotency = idempotency;
        this.posts = posts;
    }

    @KafkaListener(topics = Topics.MODERATION_EVENTS, groupId = "post-service.moderation")
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        boolean removed = "CONTENT_REMOVED".equals(e.type());
        if ((!removed && !"CONTENT_RESTORED".equals(e.type())) || !"POST".equals(e.stringField("targetType"))) return;
        if (!idempotency.firstTime("post-service.moderation", e.eventId())) return;
        posts.setRemoved(e.longField("targetId"), removed);
    }
}
