package com.socialhub.mediaservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.common.idempotency.Idempotency;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps media access in line with the owning video: the files of a PRIVATE, deleted or
 * removed video become owner/admin-only. Kafka keys events by video id, so updates for
 * one video arrive in order.
 */
@Component
public class VideoEventsConsumer {

    private static final String CONSUMER = "media-service.video-events";

    private final EventJson json;
    private final Idempotency idempotency;
    private final MediaService media;

    public VideoEventsConsumer(EventJson json, Idempotency idempotency, MediaService media) {
        this.json = json;
        this.idempotency = idempotency;
        this.media = media;
    }

    @KafkaListener(topics = Topics.VIDEO_EVENTS)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope e = json.read(message);
        if (!"VIDEO_UPSERTED".equals(e.type()) && !"VIDEO_DELETED".equals(e.type())) return;
        if (!idempotency.firstTime(CONSUMER, e.eventId())) return;

        boolean restricted = "PRIVATE".equals(e.stringField("visibility"))
                || e.boolField("deleted") || e.boolField("removed");
        List<Long> ids = new ArrayList<>();
        if (e.longField("mediaId") != null) ids.add(e.longField("mediaId"));
        if (e.longField("thumbnailMediaId") != null) ids.add(e.longField("thumbnailMediaId"));
        media.setRestricted(ids, restricted);
    }
}
