package com.socialhub.videoservice.entity;

import com.socialhub.videoservice.entity.Enums.Category;
import com.socialhub.videoservice.entity.Enums.VideoStatus;
import com.socialhub.videoservice.entity.Enums.Visibility;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@DynamicUpdate // UPDATE only changed columns: never overwrites atomically-updated counters
@Table(name = "videos")
@Getter
@Setter
@NoArgsConstructor
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "media_id", nullable = false, unique = true)
    private Long mediaId;

    @Column(name = "thumbnail_media_id")
    private Long thumbnailMediaId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private boolean removed;

    @Column(nullable = false)
    private boolean deleted;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "video_tags", joinColumns = @JoinColumn(name = "video_id"))
    @Column(name = "tag")
    private Set<String> tags = new LinkedHashSet<>();

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** Visible in public listings, feeds and search. */
    public boolean isListed() {
        return visibility == Visibility.PUBLIC && status == VideoStatus.READY && !deleted && !removed;
    }

    /** Watchable by someone who is not the owner (UNLISTED works with the link). */
    public boolean isWatchableByOthers() {
        return visibility != Visibility.PRIVATE && status == VideoStatus.READY && !deleted && !removed;
    }

    /** Set in Java (not only by the DB default) so create responses include it. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
