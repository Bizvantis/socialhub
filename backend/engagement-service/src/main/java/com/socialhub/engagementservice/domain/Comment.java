package com.socialhub.engagementservice.domain;

import com.socialhub.engagementservice.domain.Types.TargetType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate // never overwrite like_count / reply_count, which are updated atomically in SQL
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_owner_id", nullable = false)
    private Long targetOwnerId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "reply_to_user_id")
    private Long replyToUserId;

    @Column(nullable = false)
    private String body;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private boolean removed;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isVisible() {
        return !deleted && !removed;
    }

    /** Set in Java (not only by the DB default) so create responses include it. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
