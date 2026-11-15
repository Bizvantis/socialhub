package com.socialhub.userservice.repository;

import com.socialhub.userservice.entity.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    // Increment or decrement follower count atomically
    @Modifying
    @Query("UPDATE Profile p SET p.followerCount = p.followerCount + :d WHERE p.userId = :id")
    int adjustFollowers(@Param("id") Long id, @Param("d") int delta);

    // Increment or decrement following count atomically
    @Modifying
    @Query("UPDATE Profile p SET p.followingCount = p.followingCount + :d WHERE p.userId = :id")
    int adjustFollowing(@Param("id") Long id, @Param("d") int delta);

    // Find users who follow this profile
    @Query("SELECT p FROM Profile p JOIN Follow f ON f.followerId = p.userId " +
           "WHERE f.followeeId = :id ORDER BY f.createdAt DESC, p.userId DESC")
    Page<Profile> findFollowers(@Param("id") Long id, Pageable pageable);

    // Find users this profile follows
    @Query("SELECT p FROM Profile p JOIN Follow f ON f.followeeId = p.userId " +
           "WHERE f.followerId = :id ORDER BY f.createdAt DESC, p.userId DESC")
    Page<Profile> findFollowing(@Param("id") Long id, Pageable pageable);
}