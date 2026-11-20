package com.socialhub.mediaservice.service;

import com.socialhub.mediaservice.entity.Media;
import com.socialhub.mediaservice.entity.MediaStatus;
import com.socialhub.mediaservice.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Uploads started but never completed within 24h: delete any partial file and mark FAILED. */
@Component
public class StaleUploadCleanup {

    private static final Logger log = LoggerFactory.getLogger(StaleUploadCleanup.class);

    private final MediaRepository repo;
    private final StorageService storage;

    public StaleUploadCleanup(MediaRepository repo, StorageService storage) {
        this.repo = repo;
        this.storage = storage;
    }

    @Scheduled(cron = "${app.media.cleanup-cron:0 */30 * * * *}")
    @Transactional
    public void run() {
        List<Media> stale = repo.findByStatusAndCreatedAtBefore(MediaStatus.PENDING,
                Instant.now().minus(Duration.ofHours(24)), PageRequest.of(0, 500));
        for (Media m : stale) {
            storage.deletePrefix(m.keyPrefix());
            m.setStatus(MediaStatus.FAILED);
            m.setFailureReason("Upload never completed");
        }
        if (!stale.isEmpty()) log.info("Expired {} stale uploads", stale.size());
    }
}
