package com.socialhub.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    private String bio;

    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @Column(name = "is_private", nullable = false)
    private boolean privateAccount;

    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    @Column(name = "following_count", nullable = false)
    private int followingCount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}