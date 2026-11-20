package com.socialhub.engagementservice.repository;

import com.socialhub.engagementservice.domain.Comment;
import com.socialhub.engagementservice.domain.Types.TargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /** Newest top-level comments, keyset by id. */
    @Query("""
            SELECT c FROM Comment c WHERE c.targetType = :type AND c.targetId = :target AND c.parentId IS NULL
              AND c.deleted = false AND c.removed = false AND c.id < :before ORDER BY c.id DESC""")
    List<Comment> findNewest(@Param("type") TargetType type, @Param("target") Long target,
                             @Param("before") long before, Pageable page);

    /** Most liked top-level comments (offset paging: "top" order is not stable enough for keysets). */
    @Query("""
            SELECT c FROM Comment c WHERE c.targetType = :type AND c.targetId = :target AND c.parentId IS NULL
              AND c.deleted = false AND c.removed = false ORDER BY c.likeCount DESC, c.id DESC""")
    List<Comment> findTop(@Param("type") TargetType type, @Param("target") Long target, Pageable page);

    /** Replies oldest first (conversation order), keyset by id. */
    @Query("""
            SELECT c FROM Comment c WHERE c.parentId = :parent AND c.deleted = false AND c.removed = false
              AND c.id > :after ORDER BY c.id ASC""")
    List<Comment> findReplies(@Param("parent") Long parent, @Param("after") long after, Pageable page);

    // flushAutomatically: write pending entity changes first; clearAutomatically: drop managed
    // copies afterwards so later reads in the same transaction see the new counter values.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + :d WHERE c.id = :id")
    int adjustReplies(@Param("id") Long id, @Param("d") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + :d WHERE c.id = :id")
    int adjustLikes(@Param("id") Long id, @Param("d") int delta);
}
