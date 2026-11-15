package com.socialhub.userservice.service;

import com.socialhub.userservice.dto.*;
import com.socialhub.userservice.entity.*;
import com.socialhub.userservice.exception.ApiException;
import com.socialhub.userservice.repository.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final FollowRepository followRepository;

    // 1. Get my profile
    @Transactional
    public ProfileResponse getMe(Long userId, String username) {
        Profile profile = getOrCreate(userId, username);
        return toResponse(profile, userId);
    }

    // 2. Get another user's profile
    @Transactional(readOnly = true)
    public ProfileResponse getById(Long targetId, Long viewerId) {

        Profile profile = profileRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "Profile not found"));

        return toResponse(profile, viewerId);
    }

    // 3. Update my profile
    @Transactional
    public ProfileResponse update(
            Long userId,
            String username,
            UpdateProfileRequest request) {

        Profile profile = getOrCreate(userId, username);

        if (request.displayName() != null) {
            profile.setDisplayName(request.displayName().trim());
        }

        if (request.bio() != null) {
            profile.setBio(request.bio());
        }

        if (request.privateAccount() != null) {
            profile.setPrivateAccount(request.privateAccount());
        }

        profileRepository.save(profile);

        return toResponse(profile, userId);
    }

    // 4. Follow another user
    @Transactional
    public void follow(Long followerId, String username, Long followeeId) {

        if (followerId.equals(followeeId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot follow yourself");
        }

        // Ensure the follower has a profile
        getOrCreate(followerId, username);

        // Target profile must exist
        if (!profileRepository.existsById(followeeId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "User to follow not found");
        }

        // Avoid duplicate follow relationships
        if (followRepository.existsByFollowerIdAndFolloweeId(
                followerId, followeeId)) {
            return;
        }

        Follow follow = new Follow(followerId, followeeId);
        followRepository.save(follow);

        // Update both users' counts
        profileRepository.adjustFollowing(followerId, 1);
        profileRepository.adjustFollowers(followeeId, 1);
    }

    // 5. Unfollow another user
    @Transactional
    public void unfollow(Long followerId, Long followeeId) {

        int deleted = followRepository.deleteEdge(followerId, followeeId);

        if (deleted > 0) {
            profileRepository.adjustFollowing(followerId, -1);
            profileRepository.adjustFollowers(followeeId, -1);
        }
    }

    // 6. Get followers
    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> followers(
            Long userId, Long viewerId, int page, int size) {

        ensureProfileExists(userId);

        Page<Profile> result = profileRepository.findFollowers(
                userId, pageable(page, size));

        return PageResponse.from(
                result.map(profile -> toResponse(profile, viewerId)));
    }

    // 7. Get following
    @Transactional(readOnly = true)
    public PageResponse<ProfileResponse> following(
            Long userId, Long viewerId, int page, int size) {

        ensureProfileExists(userId);

        Page<Profile> result = profileRepository.findFollowing(
                userId, pageable(page, size));

        return PageResponse.from(
                result.map(profile -> toResponse(profile, viewerId)));
    }

    // Helper: Create a profile when it doesn't exist
    private Profile getOrCreate(Long userId, String username) {

        return profileRepository.findById(userId).orElseGet(() -> {

            Profile profile = new Profile();

            profile.setUserId(userId);
            profile.setUsername(username);
            profile.setDisplayName(username);
            profile.setPrivateAccount(false);
            profile.setFollowerCount(0);
            profile.setFollowingCount(0);

            return profileRepository.save(profile);
        });
    }

    // Helper: Check whether a profile exists
    private void ensureProfileExists(Long userId) {

        if (!profileRepository.existsById(userId)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND, "Profile not found");
        }
    }

    // Helper: Convert database entity into API response
    private ProfileResponse toResponse(Profile profile, Long viewerId) {

        boolean followedByMe = viewerId != null
                && !viewerId.equals(profile.getUserId())
                && followRepository.existsByFollowerIdAndFolloweeId(
                        viewerId, profile.getUserId());

        return new ProfileResponse(
                profile.getUserId(),
                profile.getUsername(),
                profile.getDisplayName(),
                profile.getBio(),
                profile.getAvatarMediaId(),
                profile.isPrivateAccount(),
                profile.getFollowerCount(),
                profile.getFollowingCount(),
                followedByMe
        );
    }

    // Helper: Pagination settings
    private Pageable pageable(int page, int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid pagination parameters");
        }

        return PageRequest.of(page, size);
    }
}