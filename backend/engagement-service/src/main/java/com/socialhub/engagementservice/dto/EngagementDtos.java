package com.socialhub.engagementservice.dto;

import com.socialhub.engagementservice.domain.Types.ReactionKind;
import com.socialhub.engagementservice.domain.Types.ShareChannel;
import com.socialhub.engagementservice.domain.Types.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class EngagementDtos {

    private EngagementDtos() {}

    public record CreateCommentRequest(
            @NotNull TargetType targetType,
            @NotNull Long targetId,
            @NotBlank @Size(max = 2200) String body,
            Long parentId) {}

    public record EditCommentRequest(@NotBlank @Size(max = 2200) String body) {}

    public record CommentResponse(
            Long id,
            TargetType targetType,
            Long targetId,
            Long authorId,
            Long parentId,
            Long replyToUserId,
            String body,
            int likeCount,
            int replyCount,
            boolean likedByMe,
            boolean byCreator,
            Instant editedAt,
            Instant createdAt) {}

    public record ReactionRequest(@NotNull TargetType targetType, @NotNull Long targetId, @NotNull ReactionKind kind) {}

    public record BookmarkRequest(@NotNull TargetType targetType, @NotNull Long targetId) {}

    public record ShareRequest(@NotNull TargetType targetType, @NotNull Long targetId, ShareChannel channel) {}

    /** Everything a card needs in one call: counts + the viewer's own state. */
    public record Summary(
            TargetType targetType,
            Long targetId,
            int likes,
            int dislikes,
            int comments,
            int shares,
            ReactionKind myReaction,
            boolean bookmarked) {}

    public record BookmarkItem(TargetType targetType, Long targetId, Instant savedAt) {}
}
