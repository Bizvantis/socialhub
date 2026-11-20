package com.socialhub.mediaservice.repository;

import com.socialhub.mediaservice.entity.Media;
import com.socialhub.mediaservice.entity.MediaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MediaRepository extends JpaRepository<Media, Long> {

    List<Media> findByIdIn(Collection<Long> ids);

    /** Uploads that were started but never completed (cleanup job). */
    List<Media> findByStatusAndCreatedAtBefore(MediaStatus status, Instant before, Pageable page);

    /** Jobs stuck in a processing state (reconciler). updated_at moves on every status change. */
    List<Media> findByStatusAndUpdatedAtBefore(MediaStatus status, Instant before, Pageable page);
}
