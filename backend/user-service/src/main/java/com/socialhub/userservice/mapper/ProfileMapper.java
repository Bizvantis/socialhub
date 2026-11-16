package com.socialhub.userservice.mapper;

import com.socialhub.userservice.dto.ProfileResponse;
import com.socialhub.userservice.entity.Profile;

public final class ProfileMapper {

    private ProfileMapper() {}

    public static ProfileResponse toResponse(Profile p, boolean followedByMe) {
        return new ProfileResponse(
                p.getUserId(),
                p.getUsername(),
                p.getDisplayName(),
                p.getBio(),
                p.getAvatarMediaId(),
                p.isPrivateAccount(),
                p.getFollowerCount(),
                p.getFollowingCount(),
                followedByMe
        );
    }
}
