package com.socialhub.postservice.repository;

import com.socialhub.postservice.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoryRepository extends JpaRepository<Story, Long> {

    Optional<Story> findByMediaId(Long mediaId);

    @Query("""
            SELECT s FROM Story s WHERE s.authorId IN :authors AND s.ready = true AND s.deleted = false
              AND s.expiresAt > :now ORDER BY s.authorId, s.id""")
    List<Story> findActiveByAuthors(@Param("authors") Collection<Long> authors, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE Story s SET s.viewCount = s.viewCount + 1 WHERE s.id = :id")
    int incrementViews(@Param("id") Long id);

    /** Expired stories past the archive window are hard-deleted by the cleanup job. */
    @Modifying
    @Query("DELETE FROM Story s WHERE s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
