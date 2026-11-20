package com.socialhub.postservice.dto;

import com.socialhub.postservice.entity.Post;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class PostDtos {

    private PostDtos() {}

    public record CreatePostRequest(
            @NotNull Post.Type type,
            @NotEmpty @Size(max = 10) List<@NotNull Long> mediaIds,
            @Size(max = 2200) String caption,
            Post.Visibility visibility) {}

    public record UpdatePostRequest(@Size(max = 2200) String caption, Post.Visibility visibility) {}

    public record PostResponse(
            Long id,
            Long authorId,
            Post.Type type,
            String caption,
            List<Long> mediaIds,
            List<String> hashtags,
            List<Long> mentionedUserIds,
            Post.Visibility visibility,
            Post.Status status,
            Instant editedAt,
            Instant createdAt) {}

    public record CreateStoryRequest(@NotNull Long mediaId, @Size(max = 500) String caption) {}

    public record StoryResponse(Long id, Long authorId, Long mediaId, String mediaType, String caption,
                                Instant createdAt, Instant expiresAt, boolean seen, Integer viewCount) {}

    /** One ring in the stories tray. */
    public record StoryRing(Long authorId, boolean allSeen, List<StoryResponse> stories) {}

    public record StoryViewer(Long viewerId, Instant viewedAt) {}

    public record HashtagResponse(String tag, int postCount) {}

    public record PostInfo(Long id, Long authorId, Post.Type type, Post.Visibility visibility, Post.Status status,
                           boolean deleted, boolean removed) {}
}
