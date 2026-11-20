
package com.socialhub.userservice.service;

import com.socialhub.userservice.dto.*;
import com.socialhub.userservice.entity.*;
import com.socialhub.userservice.exception.ApiException;
import com.socialhub.userservice.mapper.ProfileMapper;
import com.socialhub.userservice.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final FollowRepository follows;
    private final ProfileCache cache;
    private final FollowCache followCache;
    private final UserEvents events;

    public ProfileService(ProfileRepository profiles, FollowRepository follows,
                          ProfileCache cache, FollowCache followCache, UserEvents events) {
        this.profiles = profiles;
        this.follows = follows;
        this.cache = cache;
        this.followCache = followCache;
        this.events = events;
    }

    @Transactional
    public ProfileResponse getMe(Long userId, String username) {
        return ProfileMapper.toResponse(getOrCreate(userId, username), false);
    }

    // Deliberately NOT @Transactional: on a cache hit this method must not borrow
    // a DB connection. Misses run inside the repository's own read-only transaction.
    public ProfileResponse getById(Long viewerId, Long id) {
        ProfileResponse base = cache.get(id);

        boolean followed = !viewerId.equals(id)
                && followCache.isFollowing(viewerId, id);

        return base.withFollowedByMe(followed);
    }

    @Transactional
    public ProfileResponse update(Long userId, String username, UpdateProfileRequest r) {
        Profile p = getOrCreate(userId, username);

        if (r.displayName() != null) p.setDisplayName(r.displayName().trim());
        if (r.bio() != null) p.setBio(r.bio().trim());
        if (r.privateAccount() != null) p.setPrivateAccount(r.privateAccount());

        events.profileUpdated(p);
        evictAfterCommit(userId);

        return ProfileMapper.toResponse(p, false);
    }

    @Transactional
    public void follow(Long me, String myUsername, Long target) {
        if (me.equals(target)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot follow yourself");
        }

        getOrCreate(me, myUsername);
        find(target);

        if (follows.existsByFollowerIdAndFolloweeId(me, target)) {
            return;
        }

        follows.save(new Follow(me, target));
        profiles.adjustFollowing(me, 1);
        profiles.adjustFollowers(target, 1);

        events.followed(me, target, true);
        evictAfterCommit(me, target);
        evictEdgeAfterCommit(me, target);
    }

    @Transactional
    public void unfollow(Long me, Long target) {
        int removed = follows.deleteEdge(me, target);

        if (removed > 0) {
            profiles.adjustFollowing(me, -1);
            profiles.adjustFollowers(target, -1);

            events.followed(me, target, false);
            evictAfterCommit(me, target);
            evictEdgeAfterCommit(me, target);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> followers(Long id, Long viewerId, int page, int size) {
        find(id);

        return PageResponse.from(
                profiles.findFollowers(id, pageable(page, size))
                        .map(p -> ProfileMapper.toResponse(
                                p,
                                follows.existsByFollowerIdAndFolloweeId(viewerId, p.getUserId())
                        ))
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> following(Long id, Long viewerId, int page, int size) {
        find(id);

        return PageResponse.from(
                profiles.findFollowing(id, pageable(page, size))
                        .map(p -> ProfileMapper.toResponse(
                                p,
                                follows.existsByFollowerIdAndFolloweeId(viewerId, p.getUserId())
                        ))
        );
    }

    private Profile find(Long id) {
        return profiles.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    private Profile getOrCreate(Long userId, String username) {
        return profiles.findById(userId).orElseGet(() -> {
            Profile p = new Profile();
            p.setUserId(userId);
            p.setUsername(username);
            p.setDisplayName(username);
            Profile saved = profiles.save(p);
            events.profileUpdated(saved); // new profile: index it for search
            return saved;
        });
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 50)
        );
    }

    private void evictAfterCommit(Long... ids) {
        afterCommit(() -> {
            for (Long id : ids) {
                cache.evict(id);
            }
        });
    }

    private void evictEdgeAfterCommit(Long follower, Long followee) {
        afterCommit(() -> followCache.evict(follower, followee));
    }

    // Evict only once the DB change is committed, so a concurrent read
    // cannot re-cache the old value from an uncommitted transaction.
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
