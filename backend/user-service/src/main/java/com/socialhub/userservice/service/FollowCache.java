package com.socialhub.userservice.service;

import com.socialhub.userservice.repository.FollowRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Read-through Redis cache for "does follower follow followee?".
 *
 * Every profile view asks this question, so caching it removes the last
 * per-request MySQL query from GET /api/users/{id}. Entries are evicted
 * after the follow/unfollow transaction commits (see ProfileService), and
 * the cache TTL bounds staleness if an eviction is ever missed.
 *
 * Write paths (follow/unfollow idempotency) still query MySQL directly,
 * because they must see the source of truth.
 */
@Service
public class FollowCache {

    private final FollowRepository follows;

    public FollowCache(FollowRepository follows) {
        this.follows = follows;
    }

    // No @Transactional (see ProfileCache.get): a hit must not touch MySQL.
    @Cacheable(cacheNames = "follows", key = "#followerId + ':' + #followeeId")
    public boolean isFollowing(Long followerId, Long followeeId) {
        return follows.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @CacheEvict(cacheNames = "follows", key = "#followerId + ':' + #followeeId")
    public void evict(Long followerId, Long followeeId) {}
}
