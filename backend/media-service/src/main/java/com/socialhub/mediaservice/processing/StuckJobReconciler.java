package com.socialhub.mediaservice.processing;

import com.socialhub.mediaservice.entity.Media;
import com.socialhub.mediaservice.entity.MediaStatus;
import com.socialhub.mediaservice.repository.MediaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net: a video still UPLOADED after 10 minutes (task lost) or PROCESSING after 60 minutes
 * (worker crashed mid-job) is dispatched again. Workers overwrite the same output keys, so
 * re-running a job is safe.
 */
@Component
public class StuckJobReconciler {

    private static final Logger log = LoggerFactory.getLogger(StuckJobReconciler.class);

    private final MediaRepository repo;
    private final TranscodeDispatcher dispatcher;

    public StuckJobReconciler(MediaRepository repo, TranscodeDispatcher dispatcher) {
        this.repo = repo;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${app.processing.reconcile-ms:300000}", initialDelay = 60000)
    public void run() {
        redispatch(repo.findByStatusAndUpdatedAtBefore(MediaStatus.UPLOADED,
                Instant.now().minus(Duration.ofMinutes(10)), PageRequest.of(0, 50)));
        redispatch(repo.findByStatusAndUpdatedAtBefore(MediaStatus.PROCESSING,
                Instant.now().minus(Duration.ofMinutes(60)), PageRequest.of(0, 50)));
    }

    private void redispatch(List<Media> stuck) {
        for (Media m : stuck) {
            try {
                dispatcher.dispatch(m);
                log.info("Re-dispatched stuck media {} ({})", m.getId(), m.getStatus());
            } catch (Exception e) {
                log.warn("Re-dispatch of media {} failed: {}", m.getId(), e.getMessage());
            }
        }
    }
}
