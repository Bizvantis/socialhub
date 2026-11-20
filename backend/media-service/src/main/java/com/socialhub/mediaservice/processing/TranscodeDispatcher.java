package com.socialhub.mediaservice.processing;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.queue.TaskQueues;
import com.socialhub.mediaservice.entity.Media;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bridges the MEDIA_UPLOADED event (Kafka, written via the outbox) into a transcode TASK
 * (RabbitMQ). Why both brokers:
 *  - Kafka: a durable log of facts that many services read (video-service, post-service, ...).
 *  - RabbitMQ: a work queue: one worker takes one job, acks it, retries with delay, DLQ.
 *
 * Reliability: the Kafka offset is committed only after RabbitMQ confirms the message
 * (publisher confirms), so a broker hiccup re-delivers the event instead of losing the job.
 * A re-sent job is harmless: the worker overwrites the same output keys.
 */
@Component
public class TranscodeDispatcher {

    private final EventJson events;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;

    public TranscodeDispatcher(EventJson events, RabbitTemplate rabbit, ObjectMapper mapper) {
        this.events = events;
        this.rabbit = rabbit;
        this.mapper = mapper;
    }

    @KafkaListener(topics = Topics.MEDIA_EVENTS, groupId = "media-service.transcode-dispatcher")
    public void onMediaEvent(String message) {
        EventEnvelope e = events.read(message);
        if (!"MEDIA_UPLOADED".equals(e.type())) return;
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("mediaId", e.longField("mediaId"));
        task.put("ownerId", e.longField("ownerId"));
        task.put("purpose", e.stringField("purpose"));
        task.put("contentType", e.stringField("contentType"));
        task.put("objectKey", e.stringField("objectKey"));
        send(task);
    }

    /** Also used by the reconciler to re-dispatch stuck jobs. */
    public void dispatch(Media m) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("mediaId", m.getId());
        task.put("ownerId", m.getOwnerId());
        task.put("purpose", m.getPurpose().name());
        task.put("contentType", m.getContentType());
        task.put("objectKey", m.getObjectKey());
        send(task);
    }

    private void send(Map<String, Object> task) {
        var msg = MessageBuilder.withBody(mapper.writeValueAsString(task).getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT) // survives a broker restart
                .setMessageId("transcode-" + task.get("mediaId"))
                .build();
        rabbit.invoke(ops -> {
            ops.send(TaskQueues.MEDIA_TASKS, TaskQueues.TRANSCODE_KEY, msg);
            ops.waitForConfirmsOrDie(10_000);
            return null;
        });
    }
}
