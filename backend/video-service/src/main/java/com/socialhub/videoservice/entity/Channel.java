package com.socialhub.videoservice.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@DynamicUpdate // UPDATE only changed columns: never overwrites atomically-updated counters
@Table(name = "channels")
@Getter
@Setter
@NoArgsConstructor
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private Long ownerId;

    @Column(nullable = false, unique = true)
    private String handle;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @Column(name = "banner_media_id")
    private Long bannerMediaId;

    @Column(name = "subscriber_count", nullable = false)
    private int subscriberCount;

    @Column(name = "video_count", nullable = false)
    private int videoCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
