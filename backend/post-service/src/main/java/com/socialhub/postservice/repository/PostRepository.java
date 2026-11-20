package com.socialhub.postservice.repository;

import com.socialhub.postservice.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Lists use keyset pagination ("id < :before ORDER BY id DESC"): constant cost per page,
 * no duplicates or gaps while new posts arrive, unlike OFFSET.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndDeletedFalse(Long id);

    List<Post> findByIdInAndDeletedFalse(Collection<Long> ids);

    /** Profile grid. Visibility filtering (FOLLOWERS) is decided by the service. */
    @Query("""
            SELECT p FROM Post p WHERE p.authorId = :author AND p.deleted = false
              AND (:owner = true OR (p.status = 'READY' AND p.removed = false))
              AND (:includeFollowers = true OR p.visibility = 'PUBLIC')
              AND p.id < :before ORDER BY p.id DESC""")
    List<Post> findByAuthor(@Param("author") Long author, @Param("owner") boolean owner,
                            @Param("includeFollowers") boolean includeFollowers,
                            @Param("before") long before, Pageable page);

    @Query("""
            SELECT p FROM Post p WHERE p.visibility = 'PUBLIC' AND p.status = 'READY'
              AND p.deleted = false AND p.removed = false
              AND (:type IS NULL OR p.type = :type) AND p.id < :before ORDER BY p.id DESC""")
    List<Post> findPublic(@Param("type") Post.Type type, @Param("before") long before, Pageable page);
}
