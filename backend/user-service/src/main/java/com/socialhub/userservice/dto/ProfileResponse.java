package com.socialhub.userservice.dto;

public record ProfileResponse(
        Long userId,
        String username,
        String displayName,
        String bio,
        Long avatarMediaId,
        boolean privateAccount,
        int followerCount,
        int followingCount,
        boolean followedByMe
) {}