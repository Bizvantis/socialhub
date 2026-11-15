package com.socialhub.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "follows")
@IdClass(FollowId.class)
@Getter
@Setter
@NoArgsConstructor
public class Follow {

    @Id
    @Column(name = "follower_id")
    private Long followerId;

    @Id
    @Column(name = "followee_id")
    private Long followeeId;

    @Column(nullable = false)
    private String status = "ACCEPTED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Follow(Long followerId, Long followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }
}