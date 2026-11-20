package com.socialhub.postservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;

@Entity
@DynamicUpdate
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "media_id", nullable = false, unique = true)
    private Long mediaId;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    private String caption;

    @Column(nullable = false)
    private boolean ready;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public boolean isActive(Instant now) {
        return ready && !deleted && expiresAt.isAfter(now);
    }

    /** Set in Java (not only by the DB default) so create responses include it. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
