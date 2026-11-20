package com.socialhub.videoservice.service;

import com.socialhub.common.event.EventEnvelope;
import com.socialhub.common.event.EventJson;
import com.socialhub.common.event.Topics;
import com.socialhub.videoservice.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.*;

/**
 * View counting without hammering MySQL.
 *
 * 1. Dedupe: SET views:seen:{video}:{viewer} NX EX 30m, so refreshing the page does not add views.
 * 2. Count: INCR views:pending:{video} and add the id to the views:dirty set (O(1) in Redis).
 * 3. Flush: every few seconds move pending counts into MySQL with one UPDATE per video,
 *    instead of one row-lock per view on a viral video.
 *
 * Reads add the pending delta, so the count a viewer sees is never behind.
 * VIDEO_VIEWED analytics events go straight to Kafka (not the outbox): losing a few
 * analytics events in a crash is acceptable, adding a DB write per view is not.
 */
@Component
public class ViewCounter {

    private static final Logger log = LoggerFactory.getLogger(ViewCounter.class);
    private static final String DIRTY = "views:dirty";

    private final StringRedisTemplate redis;
    private final VideoRepository videos;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final EventJson json;
    private final Duration dedupeWindow;

    public ViewCounter(StringRedisTemplate redis, VideoRepository videos, TransactionTemplate tx,
                       KafkaTemplate<String, String> kafka, EventJson json,
                       @Value("${app.views.dedupe-window:30m}") Duration dedupeWindow) {
        this.redis = redis;
        this.videos = videos;
        this.tx = tx;
        this.kafka = kafka;
        this.json = json;
        this.dedupeWindow = dedupeWindow;
    }

    /** @return true if this view was counted (first view from this viewer in the window). */
    public boolean record(long videoId, String viewerKey, Long userId, long channelId) {
        Boolean first = redis.opsForValue().setIfAbsent("views:seen:" + videoId + ":" + viewerKey, "1", dedupeWindow);
        if (!Boolean.TRUE.equals(first)) return false;

        redis.opsForValue().increment("views:pending:" + videoId);
        redis.opsForSet().add(DIRTY, String.valueOf(videoId));

        Map<String, Object> p = new HashMap<>();
        p.put("videoId", videoId);
        p.put("channelId", channelId);
        p.put("userId", userId);
        try {
            kafka.send(Topics.VIDEO_VIEWS, String.valueOf(videoId),
                    json.write(EventEnvelope.of("VIDEO_VIEWED", videoId, p)));
        } catch (Exception e) {
            log.debug("Dropped VIDEO_VIEWED analytics event: {}", e.getMessage());
        }
        return true;
    }

    public long pending(long videoId) {
        String v = redis.opsForValue().get("views:pending:" + videoId);
        return v == null ? 0 : Long.parseLong(v);
    }

    public Map<Long, Long> pending(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        List<Long> list = new ArrayList<>(ids);
        List<String> vals = redis.opsForValue().multiGet(list.stream().map(id -> "views:pending:" + id).toList());
        Map<Long, Long> out = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            String v = vals == null ? null : vals.get(i);
            out.put(list.get(i), v == null ? 0L : Long.parseLong(v));
        }
        return out;
    }

    @Scheduled(fixedDelayString = "${app.views.flush-ms:5000}")
    public void flush() {
        List<String> ids = redis.opsForSet().pop(DIRTY, 500);
        if (ids == null || ids.isEmpty()) return;
        for (String id : ids) {
            String key = "views:pending:" + id;
            String n = redis.opsForValue().getAndDelete(key);
            if (n == null || "0".equals(n)) continue;
            long delta = Long.parseLong(n);
            try {
                tx.executeWithoutResult(s -> videos.addViews(Long.valueOf(id), delta));
            } catch (Exception e) {
                // Put the count back so it is retried on the next flush instead of lost.
                redis.opsForValue().increment(key, delta);
                redis.opsForSet().add(DIRTY, id);
                log.warn("View flush failed for video {}: {}", id, e.getMessage());
            }
        }
    }
}
