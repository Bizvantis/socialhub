package com.socialhub.videoservice.repository;

import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    @EntityGraph(attributePaths = "channel")
    Optional<Video> findByIdAndDeletedFalse(Long id);

    Optional<Video> findByMediaId(Long mediaId);

    @EntityGraph(attributePaths = "channel")
    List<Video> findByIdInAndDeletedFalse(Collection<Long> ids);

    /** Public catalogue (home page / browse), optional category filter. */
    @EntityGraph(attributePaths = "channel")
    @Query("""
            SELECT v FROM Video v
            WHERE v.visibility = 'PUBLIC' AND v.status = 'READY' AND v.deleted = false AND v.removed = false
              AND (:category IS NULL OR v.category = :category)
            """)
    Page<Video> findListed(@Param("category") Category category, Pageable page);

    /** Channel page: owners see everything they uploaded, visitors only listed videos. */
    @EntityGraph(attributePaths = "channel")
    @Query("""
            SELECT v FROM Video v
            WHERE v.channel.id = :channelId AND v.deleted = false
              AND (:owner = true OR (v.visibility = 'PUBLIC' AND v.status = 'READY' AND v.removed = false))
            """)
    Page<Video> findByChannel(@Param("channelId") Long channelId, @Param("owner") boolean owner, Pageable page);

    /**
     * Simple "related videos": same category or sharing a tag, most viewed first.
     * search-service (Step 27) offers a relevance-ranked version via Elasticsearch.
     */
    @EntityGraph(attributePaths = "channel")
    @Query("""
            SELECT DISTINCT v FROM Video v LEFT JOIN v.tags t
            WHERE v.id <> :id AND v.visibility = 'PUBLIC' AND v.status = 'READY'
              AND v.deleted = false AND v.removed = false
              AND (v.category = :category OR t IN :tags)
            ORDER BY v.viewCount DESC
            """)
    List<Video> findRelated(@Param("id") Long id, @Param("category") Category category,
                            @Param("tags") Collection<String> tags, Pageable page);

    @Modifying
    @Query("UPDATE Video v SET v.viewCount = v.viewCount + :n WHERE v.id = :id")
    int addViews(@Param("id") Long id, @Param("n") long n);

    List<Video> findByStatusAndCreatedAtBefore(VideoStatus status, Instant before, Pageable page);
}
