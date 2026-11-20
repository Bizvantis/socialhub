package com.socialhub.postservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@DynamicUpdate
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {

    public enum Type { IMAGE, CAROUSEL, REEL }

    /** PUBLIC: anyone. FOLLOWERS: only accepted followers (and the author). */
    public enum Visibility { PUBLIC, FOLLOWERS }

    public enum Status { PROCESSING, READY, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private boolean removed;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "edited_at")
    private Instant editedAt;

    /** Ordered media ids; @OrderColumn keeps the carousel order in post_media.position. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "post_media", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "position")
    @Column(name = "media_id")
    private List<Long> mediaIds = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    public boolean isVisibleState() {
        return status == Status.READY && !deleted && !removed;
    }

    /** Set in Java (not only by the DB default) so create responses include it. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
