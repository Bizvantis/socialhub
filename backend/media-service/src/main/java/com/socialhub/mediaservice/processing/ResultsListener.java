package com.socialhub.mediaservice.processing;

import com.socialhub.common.queue.TaskQueues;
import com.socialhub.mediaservice.service.MediaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Applies video-worker results. Idempotent by state: markProcessed/markFailed ignore media
 * that is already READY or DELETED, so a duplicate or late result changes nothing.
 * MEDIA_READY / MEDIA_FAILED then flow to Kafka through the outbox.
 */
@Component
public class ResultsListener {

    private final MediaService media;
    private final ObjectMapper mapper;

    public ResultsListener(MediaService media, ObjectMapper mapper) {
        this.media = media;
        this.mapper = mapper;
    }

    @RabbitListener(queues = TaskQueues.MEDIA_RESULTS_QUEUE)
    public void onResult(byte[] body) {
        Map<String, Object> r = mapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        Long id = num(r.get("mediaId"));
        switch (String.valueOf(r.get("status"))) {
            case "PROCESSING" -> media.markProcessing(id);
            case "READY" -> media.markProcessed(id, (String) r.get("thumbnailKey"), (String) r.get("playbackKey"),
                    intOrNull(r.get("durationSeconds")), intOrNull(r.get("width")), intOrNull(r.get("height")));
            case "FAILED" -> media.markFailed(id, String.valueOf(r.getOrDefault("reason", "Processing failed")));
            default -> { }
        }
    }

    private static Long num(Object o) {
        return o instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(o));
    }

    private static Integer intOrNull(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }
}
