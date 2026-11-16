package com.socialhub.userservice.dto;

import java.io.Serializable;

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
) implements Serializable {

    public ProfileResponse withFollowedByMe(boolean v) {
        return new ProfileResponse(
                userId, username, displayName, bio,
                avatarMediaId, privateAccount,
                followerCount, followingCount, v
        );
    }
}