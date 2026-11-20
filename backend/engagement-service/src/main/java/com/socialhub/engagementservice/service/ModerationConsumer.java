package com.socialhub.engagementservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Applies moderation takedowns/restores to comments. */
@Component
public class ModerationConsumer {

    private final EventJson json;
    private final Idempotency idempotency;
    private final CommentService comments;

    public ModerationConsumer(EventJson json, Idempotency idempotency, CommentService comments) {
        this.json = json;
        this.idempotency = idempotency;
        this.comments = comments;
    }

    @KafkaListener(topics = Topics.MODERATION_EVENTS, groupId = "engagement-service.moderation")
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        boolean removed = "CONTENT_REMOVED".equals(e.type());
        if ((!removed && !"CONTENT_RESTORED".equals(e.type())) || !"COMMENT".equals(e.stringField("targetType"))) return;
        if (!idempotency.firstTime("engagement-service.moderation", e.eventId())) return;
        comments.setRemoved(e.longField("targetId"), removed);
    }
}
