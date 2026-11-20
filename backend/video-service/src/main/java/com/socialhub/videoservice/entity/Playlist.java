package com.socialhub.videoservice.entity;

import com.socialhub.videoservice.entity.Enums.Visibility;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@DynamicUpdate // UPDATE only changed columns: never overwrites atomically-updated counters
@Table(name = "playlists")
@Getter
@Setter
@NoArgsConstructor
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility;

    @Column(name = "video_count", nullable = false)
    private int videoCount;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** Set in Java (not only by the DB default) so create responses include it. */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
