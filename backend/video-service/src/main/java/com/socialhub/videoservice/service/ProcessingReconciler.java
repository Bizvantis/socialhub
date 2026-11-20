package com.socialhub.videoservice.service;

import com.socialhub.videoservice.client.MediaClient;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Video;
import com.socialhub.videoservice.repository.VideoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for a race the events alone cannot close: if media-service finishes processing
 * between "video-service read the media status" and "video row committed", the MEDIA_READY
 * event finds no video yet. Every minute, videos still PROCESSING after 2 minutes are
 * re-checked directly against media-service.
 */
@Component
public class ProcessingReconciler {

    private static final Logger log = LoggerFactory.getLogger(ProcessingReconciler.class);

    private final VideoRepository videos;
    private final MediaClient media;
    private final VideoService videoService;

    public ProcessingReconciler(VideoRepository videos, MediaClient media, VideoService videoService) {
        this.videos = videos;
        this.media = media;
        this.videoService = videoService;
    }

    @Scheduled(fixedDelayString = "${app.videos.reconcile-ms:60000}", initialDelay = 30000)
    public void run() {
        List<Video> stuck = videos.findByStatusAndCreatedAtBefore(VideoStatus.PROCESSING,
                Instant.now().minus(Duration.ofMinutes(2)), PageRequest.of(0, 100));
        for (Video v : stuck) {
            try {
                MediaClient.MediaInfo m = media.get(v.getMediaId());
                if (m.ready()) videoService.onMediaProcessed(v.getMediaId(), true, m.durationSeconds());
                else if (m.failed()) videoService.onMediaProcessed(v.getMediaId(), false, null);
            } catch (Exception e) {
                log.debug("Reconcile skipped video {}: {}", v.getId(), e.getMessage());
            }
        }
    }
}
