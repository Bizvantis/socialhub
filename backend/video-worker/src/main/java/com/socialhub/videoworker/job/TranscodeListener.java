package com.socialhub.videoworker.job;

import com.socialhub.common.queue.TaskQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumes transcode tasks (prefetch 1: a worker holds one CPU-heavy job at a time; scale out by
 * running more worker instances, RabbitMQ distributes jobs round-robin).
 *
 * Failure handling:
 *  - attempt fails and attempts remain: reject without requeue -> dead-lettered to the retry
 *    queue -> back to the work queue after the delay;
 *  - last attempt fails, or the input is not a video at all: report FAILED to media-service and park the
 *    message in media.transcode.dlq for inspection.
 */
@Component
public class TranscodeListener {

    private static final Logger log = LoggerFactory.getLogger(TranscodeListener.class);

    private final TranscodeJob job;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final int maxAttempts;

    public TranscodeListener(TranscodeJob job, RabbitTemplate rabbit, ObjectMapper mapper,
                             @Value("${app.processing.max-attempts:3}") int maxAttempts) {
        this.job = job;
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.maxAttempts = maxAttempts;
    }

    @RabbitListener(queues = TaskQueues.TRANSCODE_QUEUE, concurrency = "${app.processing.concurrency:1}")
    public void onTask(Message message) {
        Map<String, Object> task = mapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {});
        long mediaId = ((Number) task.get("mediaId")).longValue();
        long attempt = TaskQueues.previousAttempts(message, TaskQueues.TRANSCODE_QUEUE) + 1;
        log.info("Transcode media {} attempt {}/{}", mediaId, attempt, maxAttempts);

        publish(Map.of("mediaId", mediaId, "status", "PROCESSING"));
        try {
            TranscodeJob.Output out = job.run(mediaId, (String) task.get("objectKey"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("mediaId", mediaId);
            r.put("status", "READY");
            r.put("thumbnailKey", out.thumbnailKey());
            r.put("playbackKey", out.playbackKey());
            r.put("durationSeconds", out.durationSeconds());
            r.put("width", out.width());
            r.put("height", out.height());
            publish(r);
        } catch (IllegalArgumentException notAVideo) {
            giveUp(message, mediaId, notAVideo.getMessage());     // retrying cannot help
        } catch (Exception e) {
            if (attempt >= maxAttempts) {
                giveUp(message, mediaId, e.getMessage());
            } else {
                log.warn("Media {} attempt {} failed, will retry: {}", mediaId, attempt, e.getMessage());
                throw new AmqpRejectAndDontRequeueException("retry later", e);
            }
        }
    }

    private void giveUp(Message message, long mediaId, String reason) {
        log.error("Media {} failed permanently: {}", mediaId, reason);
        publish(Map.of("mediaId", mediaId, "status", "FAILED", "reason", reason == null ? "Processing failed" : reason));
        rabbit.send(TaskQueues.MEDIA_DLX, TaskQueues.TRANSCODE_KEY + ".dlq", message); // parking lot
    }

    private void publish(Map<String, Object> result) {
        var msg = MessageBuilder.withBody(mapper.writeValueAsString(result).getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json").setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
        rabbit.invoke(ops -> {
            ops.send(TaskQueues.MEDIA_RESULTS_EXCHANGE, TaskQueues.MEDIA_RESULTS_KEY, msg);
            ops.waitForConfirmsOrDie(10_000);
            return null;
        });
    }

    /** The worker declares the same topology as media-service (declarations are idempotent). */
    @org.springframework.context.annotation.Configuration
    static class Topology {
        @Bean
        Declarables workerTaskQueues(@Value("${app.processing.retry-delay-ms:15000}") long retryDelayMs) {
            return TaskQueues.withRetry(TaskQueues.MEDIA_TASKS, TaskQueues.MEDIA_DLX,
                    TaskQueues.TRANSCODE_QUEUE, TaskQueues.TRANSCODE_KEY, retryDelayMs);
        }

        @Bean
        Declarables workerResultQueues() {
            return TaskQueues.simple(TaskQueues.MEDIA_RESULTS_EXCHANGE, TaskQueues.MEDIA_RESULTS_QUEUE,
                    TaskQueues.MEDIA_RESULTS_KEY);
        }
    }
}
