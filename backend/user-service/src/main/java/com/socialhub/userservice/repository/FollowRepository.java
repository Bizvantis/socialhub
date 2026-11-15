package com.socialhub.userservice.repository;

import com.socialhub.userservice.entity.Follow;
import com.socialhub.userservice.entity.FollowId;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    // Check whether one user follows another
    boolean existsByFollowerIdAndFolloweeId(
            Long followerId,
            Long followeeId
    );

    // Delete a follow relationship
    @Modifying
    @Query("DELETE FROM Follow f WHERE f.followerId = :a AND f.followeeId = :b")
    int deleteEdge(
            @Param("a") Long follower,
            @Param("b") Long followee
    );
}